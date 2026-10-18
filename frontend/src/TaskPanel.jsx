import { useEffect, useState } from 'react';
import { api, STATUSES, PRIORITIES, label } from './api.js';

export default function TaskPanel({ me, taskId, projectMembers, onClose, onChanged }) {
  const isStaff = me.role !== 'client_user';
  const isClient = !isStaff;
  const [task, setTask] = useState(null);
  const [comments, setComments] = useState([]);
  const [files, setFiles] = useState([]);
  const [entries, setEntries] = useState([]);
  const [comment, setComment] = useState({ body: '', visibility: 'internal' });
  const [time, setTime] = useState({ minutes: '', note: '' });
  const [upload, setUpload] = useState({ file: null, visibility: 'internal' });
  const [error, setError] = useState('');

  async function load() {
    const [t, c, f] = await Promise.all([
      api.get(`/tasks/${taskId}`),
      api.get(`/tasks/${taskId}/comments`),
      api.get(`/tasks/${taskId}/files`),
    ]);
    setTask(t);
    setComments(c);
    setFiles(f);
    if (isStaff) setEntries(await api.get(`/tasks/${taskId}/time-entries`));
  }

  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, [taskId]);

  const run = (fn) => async (e) => {
    e?.preventDefault?.();
    setError('');
    try {
      await fn();
      await load();
      onChanged();
    } catch (err) {
      setError(err.message);
    }
  };

  const update = (fields) => run(() => api.patch(`/tasks/${taskId}`, fields))();

  if (!task) return <aside className="panel"><p className="muted">{error || 'Loading…'}</p></aside>;
  const internalTask = task.visibility === 'internal';

  return (
    <aside className="panel">
      <button className="link close" onClick={onClose}>✕</button>
      <h3>{task.title}</h3>
      {task.description && <p>{task.description}</p>}
      {error && <p className="error">{error}</p>}

      {isStaff ? (
        <div className="fields">
          <label>Status
            <select value={task.status} onChange={(e) => update({ status: e.target.value })}>
              {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
            </select>
          </label>
          <label>Priority
            <select value={task.priority} onChange={(e) => update({ priority: e.target.value })}>
              {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
            </select>
          </label>
          <label>Assignee
            <select value={task.assigneeMembershipId ?? ''} onChange={(e) => e.target.value && update({ assigneeMembershipId: Number(e.target.value) })}>
              <option value="">{task.needsReassignment ? '— needs a new owner —' : 'unassigned'}</option>
              {projectMembers.map((m) => <option key={m.membershipId} value={m.membershipId}>{m.name}</option>)}
            </select>
          </label>
          <label>Visibility
            <select value={task.visibility} onChange={(e) => update({ visibility: e.target.value })}>
              <option value="internal">internal</option>
              <option value="client">client-visible</option>
            </select>
          </label>
          <label>Due
            <input type="date" value={task.dueDate ?? ''} onChange={(e) => update({ dueDate: e.target.value || null })} />
          </label>
        </div>
      ) : (
        <p className="muted">Status: <strong>{label(task.status)}</strong> · Priority: {task.priority}{task.dueDate ? ` · Due ${task.dueDate}` : ''}</p>
      )}

      <h4>Comments</h4>
      {comments.map((c) => (
        <div key={c.id} className={`comment ${c.visibility}`}>
          <span className="muted small">{c.authorName}{isStaff ? ` · ${c.visibility}` : ''}</span>
          <p>{c.body}</p>
        </div>
      ))}
      <form className="stack" onSubmit={run(async () => {
        await api.post(`/tasks/${taskId}/comments`, comment);
        setComment({ ...comment, body: '' });
      })}>
        <textarea placeholder="Write a comment…" value={comment.body} onChange={(e) => setComment({ ...comment, body: e.target.value })} required />
        {isStaff && (
          <select value={internalTask ? 'internal' : comment.visibility} disabled={internalTask}
                  onChange={(e) => setComment({ ...comment, visibility: e.target.value })}>
            <option value="internal">internal note</option>
            <option value="client">visible to client</option>
          </select>
        )}
        <button type="submit">Comment</button>
      </form>

      <h4>Files</h4>
      {files.map((f) => (
        <div key={f.id} className="row">
          <button className="link" onClick={() => api.download(f.id, f.filename)}>{f.filename}</button>
          <span className={`badge ${f.approvalStatus}`}>{label(f.approvalStatus)}</span>
          {isStaff && <span className="muted small">{f.visibility}</span>}
          {isClient && (
            <span>
              <button className="link" onClick={run(() => api.post(`/files/${f.id}/review`, { decision: 'approved' }))}>approve</button>
              {' '}
              <button className="link" onClick={run(() => api.post(`/files/${f.id}/review`, { decision: 'needs_changes' }))}>needs changes</button>
            </span>
          )}
        </div>
      ))}
      {files.length === 0 && <p className="muted small">No files.</p>}
      {isStaff && (
        <form className="inline" onSubmit={run(async () => {
          const form = new FormData();
          form.append('file', upload.file);
          form.append('visibility', upload.visibility);
          await api.upload(`/tasks/${taskId}/files`, form);
          setUpload({ ...upload, file: null });
        })}>
          <input type="file" onChange={(e) => setUpload({ ...upload, file: e.target.files[0] })} required />
          <select value={internalTask ? 'internal' : upload.visibility} disabled={internalTask}
                  onChange={(e) => setUpload({ ...upload, visibility: e.target.value })}>
            <option value="internal">internal</option>
            <option value="client">client-visible</option>
          </select>
          <button type="submit">Upload</button>
        </form>
      )}

      {isStaff && (
        <>
          <h4>Time</h4>
          {entries.map((t) => (
            <div key={t.id} className="row small">
              <span>{t.entryDate} · {t.memberName}</span><span>{t.minutes} min {t.note ? `— ${t.note}` : ''}</span>
            </div>
          ))}
          <form className="inline" onSubmit={run(async () => {
            await api.post(`/tasks/${taskId}/time-entries`, { minutes: Number(time.minutes), note: time.note });
            setTime({ minutes: '', note: '' });
          })}>
            <input type="number" min="1" max="1440" placeholder="minutes" value={time.minutes} onChange={(e) => setTime({ ...time, minutes: e.target.value })} required />
            <input placeholder="note" value={time.note} onChange={(e) => setTime({ ...time, note: e.target.value })} />
            <button type="submit">Log time</button>
          </form>
        </>
      )}
    </aside>
  );
}
