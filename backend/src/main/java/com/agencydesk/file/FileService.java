package com.agencydesk.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;

@Service
public class FileService {

    private final JdbcClient jdbc;
    private final AccessService access;
    private final Path uploadDir;

    public FileService(JdbcClient jdbc, AccessService access, @Value("${agencydesk.upload-dir}") String uploadDir) {
        this.jdbc = jdbc;
        this.access = access;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public record FileView(long id, long taskId, String filename, String contentType, long sizeBytes,
                           String visibility, String approvalStatus, String uploaderName,
                           String reviewedByName, OffsetDateTime reviewedAt, OffsetDateTime createdAt) {}

    record StoredFile(long id, long taskId, String filename, String contentType, String storagePath,
                      String visibility) {}

    public record Download(String filename, String contentType, InputStream content) {}

    private static final String SELECT = """
            SELECT f.id, f.task_id, f.filename, f.content_type, f.size_bytes, f.visibility, f.approval_status,
                   uu.name AS uploader_name, ru.name AS reviewed_by_name, f.reviewed_at, f.created_at
            FROM files f
            JOIN memberships um ON um.id = f.uploader_membership_id AND um.agency_id = f.agency_id
            JOIN users uu ON uu.id = um.user_id
            LEFT JOIN memberships rm ON rm.id = f.reviewed_by_membership_id AND rm.agency_id = f.agency_id
            LEFT JOIN users ru ON ru.id = rm.user_id
            """;

    public List<FileView> list(Actor actor, long taskId) {
        var task = access.requireTask(actor, taskId);
        return jdbc.sql(SELECT + " WHERE f.task_id = ? AND f.agency_id = ? ORDER BY f.id")
                .params(taskId, actor.agencyId())
                .query(FileView.class)
                .stream()
                .filter(f -> access.canSeeChild(actor, task, f.visibility()))
                .toList();
    }

    public FileView upload(Actor actor, long taskId, MultipartFile file, String requestedVisibility) {
        access.requireStaff(actor);
        var task = access.requireTask(actor, taskId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("file is required");
        }
        String visibility = "internal".equals(task.visibility()) ? "internal"
                : "client".equals(requestedVisibility) ? "client" : "internal";
        // Stored under a random name inside agency/<id>/, never under the user-supplied name (no path traversal).
        String relative = "agency-" + actor.agencyId() + "/" + UUID.randomUUID();
        Path target = uploadDir.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("could not store file", e);
        }
        String name = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
        long id = jdbc.sql("""
                INSERT INTO files (agency_id, task_id, uploader_membership_id, filename, content_type, size_bytes,
                                   storage_path, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """)
                .params(actor.agencyId(), taskId, actor.membershipId(), name, file.getContentType(), file.getSize(),
                        relative, visibility)
                .query(Long.class)
                .single();
        return get(actor, id);
    }

    public Download download(Actor actor, long fileId) {
        StoredFile f = requireFile(actor, fileId);
        try {
            return new Download(f.filename(), f.contentType(), Files.newInputStream(uploadDir.resolve(f.storagePath())));
        } catch (IOException e) {
            throw ApiException.notFound("file content");
        }
    }

    /** Only the client can approve or request changes, and only on a file they are allowed to see. */
    public FileView review(Actor actor, long fileId, String decision) {
        if (!actor.isClient()) {
            throw ApiException.forbidden("only the client can review files");
        }
        if (!"approved".equals(decision) && !"needs_changes".equals(decision)) {
            throw ApiException.badRequest("decision must be approved or needs_changes");
        }
        requireFile(actor, fileId);
        jdbc.sql("""
                UPDATE files SET approval_status = ?, reviewed_by_membership_id = ?, reviewed_at = now()
                WHERE id = ? AND agency_id = ?
                """)
                .params(decision, actor.membershipId(), fileId, actor.agencyId())
                .update();
        return get(actor, fileId);
    }

    private FileView get(Actor actor, long fileId) {
        return jdbc.sql(SELECT + " WHERE f.id = ? AND f.agency_id = ?")
                .params(fileId, actor.agencyId())
                .query(FileView.class)
                .single();
    }

    /** Tenant check + task access + visibility, all before anything about the file is returned. */
    private StoredFile requireFile(Actor actor, long fileId) {
        var f = jdbc.sql("""
                SELECT id, task_id, filename, content_type, storage_path, visibility
                FROM files WHERE id = ? AND agency_id = ?
                """)
                .params(fileId, actor.agencyId())
                .query(StoredFile.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("file"));
        var task = access.requireTask(actor, f.taskId());
        if (!access.canSeeChild(actor, task, f.visibility())) {
            throw ApiException.notFound("file");
        }
        return f;
    }
}
