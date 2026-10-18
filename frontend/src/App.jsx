import { useEffect, useState } from 'react';
import { api, session } from './api.js';
import Login from './Login.jsx';
import AcceptInvite from './AcceptInvite.jsx';
import Projects from './Projects.jsx';
import ProjectView from './ProjectView.jsx';
import Invites from './Invites.jsx';

export default function App() {
  const inviteToken = new URLSearchParams(window.location.search).get('token');
  const [me, setMe] = useState(null);           // Actor from /auth/me
  const [memberships, setMemberships] = useState([]);
  const [needsPick, setNeedsPick] = useState(false);
  const [view, setView] = useState({ name: 'projects' });

  async function loadSession() {
    if (!session.token) return;
    try {
      const list = await api.get('/auth/memberships');
      setMemberships(list);
      try {
        setMe(await api.get('/auth/me'));
        setNeedsPick(false);
      } catch (e) {
        if (e.status === 403) setNeedsPick(true);
        else throw e;
      }
    } catch {
      session.token = null;
      setMe(null);
    }
  }

  useEffect(() => {
    loadSession();
  }, []);

  async function pick(membershipId) {
    await api.post('/auth/select', { membershipId });
    setView({ name: 'projects' });
    await loadSession();
  }

  async function logout() {
    try {
      await api.post('/auth/logout');
    } finally {
      session.token = null;
      setMe(null);
      setMemberships([]);
      setNeedsPick(false);
    }
  }

  if (inviteToken) return <AcceptInvite token={inviteToken} />;
  if (!session.token || (!me && !needsPick)) {
    return <Login onLoggedIn={loadSession} />;
  }
  if (needsPick) {
    return (
      <div className="center">
        <div className="card narrow">
          <h2>Choose a workspace</h2>
          <p className="muted">Your account belongs to more than one agency.</p>
          {memberships.map((m) => (
            <button key={m.membershipId} className="pick" onClick={() => pick(m.membershipId)}>
              <strong>{m.agencyName}</strong>
              <span>{m.role.replace('_', ' ')}{m.clientName ? ` · ${m.clientName}` : ''}</span>
            </button>
          ))}
          <button className="link" onClick={logout}>Log out</button>
        </div>
      </div>
    );
  }

  const current = memberships.find((m) => m.membershipId === me.membershipId);
  const isClient = me.role === 'client_user';

  return (
    <div>
      <header className={isClient ? 'top client' : 'top'}>
        <button className="brand" onClick={() => setView({ name: 'projects' })}>AgencyDesk</button>
        <span className="ws">
          {current?.agencyName} · {me.role.replace('_', ' ')}
          {isClient && current?.clientName ? ` · ${current.clientName} portal` : ''}
        </span>
        <span className="spacer" />
        {me.role === 'agency_admin' && (
          <button className="link light" onClick={() => setView({ name: 'invites' })}>Invites</button>
        )}
        {memberships.length > 1 && (
          <select value={me.membershipId} onChange={(e) => pick(Number(e.target.value))}>
            {memberships.map((m) => (
              <option key={m.membershipId} value={m.membershipId}>
                {m.agencyName} ({m.role.replace('_', ' ')})
              </option>
            ))}
          </select>
        )}
        <button className="link light" onClick={logout}>Log out</button>
      </header>
      <main>
        {view.name === 'projects' && (
          <Projects me={me} onOpen={(id) => setView({ name: 'project', id })} />
        )}
        {view.name === 'project' && <ProjectView me={me} projectId={view.id} key={view.id} />}
        {view.name === 'invites' && <Invites />}
      </main>
    </div>
  );
}
