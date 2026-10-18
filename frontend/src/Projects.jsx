import { useEffect, useState } from 'react';
import { api } from './api.js';

export default function Projects({ me, onOpen }) {
  const [projects, setProjects] = useState([]);
  const [clients, setClients] = useState([]);
  const [form, setForm] = useState({ clientId: '', name: '' });
  const [error, setError] = useState('');
  const isAdmin = me.role === 'agency_admin';

  async function load() {
    setProjects(await api.get('/projects'));
    if (isAdmin) setClients(await api.get('/clients'));
  }

  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, []);

  async function create(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/projects', { clientId: Number(form.clientId), name: form.name });
      setForm({ clientId: '', name: '' });
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section>
      <h2>{me.role === 'client_user' ? 'Your projects' : 'Projects'}</h2>
      {error && <p className="error">{error}</p>}
      <div className="grid">
        {projects.map((p) => (
          <button key={p.id} className="card project" onClick={() => onOpen(p.id)}>
            <strong>{p.name}</strong>
            <span className="muted">{p.clientName}</span>
          </button>
        ))}
        {projects.length === 0 && <p className="muted">No projects visible to you.</p>}
      </div>
      {isAdmin && (
        <form className="card inline" onSubmit={create}>
          <strong>New project</strong>
          <select value={form.clientId} onChange={(e) => setForm({ ...form, clientId: e.target.value })} required>
            <option value="">Client…</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <input placeholder="Project name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <button type="submit">Create</button>
        </form>
      )}
    </section>
  );
}
