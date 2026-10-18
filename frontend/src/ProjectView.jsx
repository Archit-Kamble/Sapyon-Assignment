import { useEffect, useState } from 'react';
import { api, STATUSES, PRIORITIES, label } from './api.js';
import TaskPanel from './TaskPanel.jsx';

export default function ProjectView({ me, projectId }) {
  const isStaff = me.role !== 'client_user';
  const isAdmin = me.role === 'agency_admin';
  const [project, setProject] = useState(null);
  const [dash, setDash] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [members, setMembers] = useState([]);
  const [hours, setHours] = useState([]);
  const [filters, setFilters] = useState({ q: '', priority: '' });
  const [openTask, setOpenTask] = useState(null);
  const [newTask, setNewTask] = useState({ title: '', visibility: 'internal', priority: 'medium' });
  const [error, setError] = useState('');

  async function load() {
    const qs = new URLSearchParams({ projectId });
    if (filters.q) qs.set('q', filters.q);
    if (filters.priority) qs.set('priority', filters.priority);
    const [p, d, t] = await Promise.all([
      api.get(`/projects/${projectId}`),
      api.get(`/projects/${projectId}/dashboard`),
      api.get(`/tasks?${qs}`),
    ]);
    setProject(p);
    setDash(d);
    setTasks(t);
    if (isStaff) {
      setMembers(await api.get(`/projects/${projectId}/members`));
      setHours(await api.get(`/projects/${projectId}/hours`));
    }
  }

  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, [filters]);

  async function createTask(e) {
    e.preventDefault();
    try {
      await api.post(`/projects/${projectId}/tasks`, newTask);
      setNewTask({ title: '', visibility: 'internal', priority: 'medium' });
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function removeMember(m) {
    if (!window.confirm(`Remove ${m.name} from this project? Their tasks will be unassigned; their logged hours are kept.`)) return;
    const r = await api.del(`/projects/${projectId}/members/${m.membershipId}`);
    setError('');
    window.alert(`${r.tasksUnassigned} task(s) now need reassignment.`);
    load();
  }

  if (error) return <p className="error">{error}</p>;
  if (!project || !dash) return <p className="muted">Loading…</p>;

  return (
    <section>
      <h2>{project.name} <span className="muted small">· {project.clientName}</span></h2>

      <div className="stats">
        {STATUSES.map((s) => (
          <div key={s} className="stat"><span>{label(s)}</span><strong>{dash.tasksByStatus[s]}</strong></div>
        ))}
        <div className="stat"><span>total tasks</span><strong>{dash.totalTasks}</strong></div>
        <div className="stat"><span>hours logged</span><strong>{dash.hoursLogged}</strong></div>
      </div>
      {!isStaff && <p className="muted small">You see only work your agency has shared with you.</p>}

      <div className="toolbar">
        <input placeholder="Search tasks…" value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
        <select value={filters.priority} onChange={(e) => setFilters({ ...filters, priority: e.target.value })}>
          <option value="">Any priority</option>
          {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
      </div>

      <div className="board">
        {STATUSES.map((s) => (
          <div key={s} className="column">
            <h4>{label(s)}</h4>
            {tasks.filter((t) => t.status === s).map((t) => (
              <button key={t.id} className={`task ${t.visibility}`} onClick={() => setOpenTask(t.id)}>
                <span>{t.title}</span>
                <span className="meta">
                  <em className={`pri ${t.priority}`}>{t.priority}</em>
                  {isStaff && <em className={`vis ${t.visibility}`}>{t.visibility}</em>}
                  {t.needsReassignment && <em className="warn">needs owner</em>}
                </span>
                <span className="muted small">{t.assigneeName || 'unassigned'}{t.dueDate ? ` · due ${t.dueDate}` : ''}</span>
              </button>
            ))}
          </div>
        ))}
      </div>

      {isStaff && (
        <form className="card inline" onSubmit={createTask}>
          <strong>New task</strong>
          <input placeholder="Title" value={newTask.title} onChange={(e) => setNewTask({ ...newTask, title: e.target.value })} required />
          <select value={newTask.priority} onChange={(e) => setNewTask({ ...newTask, priority: e.target.value })}>
            {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
          </select>
          <select value={newTask.visibility} onChange={(e) => setNewTask({ ...newTask, visibility: e.target.value })}>
            <option value="internal">internal (agency only)</option>
            <option value="client">client-visible</option>
          </select>
          <button type="submit">Add</button>
        </form>
      )}

      {isStaff && (
        <div className="two">
          <div className="card">
            <strong>Team on this project</strong>
            {members.map((m) => (
              <div key={m.membershipId} className="row">
                <span>{m.name} <span className="muted small">{label(m.role)}</span></span>
                {isAdmin && <button className="link danger" onClick={() => removeMember(m)}>remove</button>}
              </div>
            ))}
          </div>
          <div className="card">
            <strong>Hours by person</strong>
            {hours.map((h) => (
              <div key={h.memberName} className="row"><span>{h.memberName}</span><span>{h.hours} h</span></div>
            ))}
            {hours.length === 0 && <p className="muted small">No time logged yet.</p>}
          </div>
        </div>
      )}

      {openTask && (
        <TaskPanel me={me} taskId={openTask} projectMembers={members}
                   onClose={() => setOpenTask(null)} onChanged={load} />
      )}
    </section>
  );
}
