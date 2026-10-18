package com.agencydesk.file;

import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agencydesk.auth.Actor;
import com.agencydesk.file.FileService.FileView;

@RestController
@RequestMapping("/api")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    public record Review(String decision) {}

    @GetMapping("/tasks/{taskId}/files")
    public List<FileView> list(Actor actor, @PathVariable long taskId) {
        return service.list(actor, taskId);
    }

    @PostMapping("/tasks/{taskId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    public FileView upload(Actor actor, @PathVariable long taskId, @RequestParam("file") MultipartFile file,
                           @RequestParam(value = "visibility", required = false) String visibility) {
        return service.upload(actor, taskId, file, visibility);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<InputStreamResource> download(Actor actor, @PathVariable long id) {
        var d = service.download(actor, id);
        MediaType type = d.contentType() != null ? MediaType.parseMediaType(d.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.filename()).build().toString())
                .body(new InputStreamResource(d.content()));
    }

    @PostMapping("/files/{id}/review")
    public FileView review(Actor actor, @PathVariable long id, @RequestBody Review req) {
        return service.review(actor, id, req.decision());
    }
}
