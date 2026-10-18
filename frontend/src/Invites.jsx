import { useEffect, useState } from 'react';
import { api } from './api.js';

export default function Invites() {
  const [invites, setInvites] = useState([]);
  const [clients, setClients] = useState([]);
  const [form, setForm] = useState({ email: '', role: 'agency_member', clientId: '' });
  const [last, setLast] = useState(null);
  const [error, setError] = useState('');

  async function load() {
    setInvites(await api.get('/invites'));
    setClients(await api.get('/clients'));
  }

  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, []);

  async function send(e) {
    e.preventDefault();
    setError('');
    try {
      const body = { email: form.email, role: form.role, clientId: form.role === 'client_user' ? Number(form.clientId) : null };
      const inv = await api.post('/invites', body);
      setLast(inv);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  const link = (t) => `${window.location.origin}/?token=${t}`;

  return (
    <section>
      <h2>Invites</h2>
      <form className="card inline" onSubmit={send}>
        <input placeholder="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
          <option value="agency_member">agency member</option>
          <option value="agency_admin">agency admin</option>
          <option value="client_user">client user</option>
        </select>
        {form.role === 'client_user' && (
          <select value={form.clientId} onChange={(e) => setForm({ ...form, clientId: e.target.value })} required>
            <option value="">Client…</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        )}
        <button type="submit">Send invite</button>
      </form>
      {error && <p className="error">{error}</p>}
      {last && (
        <p className="card">No email server in this demo. Invite link: <code>{link(last.token)}</code>
          <br /><span className="muted small">Sending again for the same email returns this same invite.</span></p>
      )}
      <table>
        <thead><tr><th>Email</th><th>Role</th><th>Status</th><th>Link</th></tr></thead>
        <tbody>
          {invites.map((i) => (
            <tr key={i.id}>
              <td>{i.email}</td><td>{i.role.replace('_', ' ')}</td><td>{i.status}</td>
              <td>{i.status === 'pending' ? <code className="small">{link(i.token)}</code> : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
