import { useState } from 'react';
import { api, session } from './api.js';

const DEMO = [
  'admin@pixelco.test',
  'maya@pixelco.test',
  'admin@brandhive.test',
  'riya@client.test',
  'omar@globex.test',
];

export default function Login({ onLoggedIn }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError('');
    try {
      const res = await api.post('/auth/login', { email, password });
      session.token = res.token;
      onLoggedIn();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="center">
      <form className="card narrow" onSubmit={submit}>
        <h2>AgencyDesk</h2>
        <p className="muted">Agency staff and clients sign in here.</p>
        <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} autoFocus /></label>
        <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <button type="submit">Sign in</button>
        <div className="demo">
          <span className="muted">Demo accounts (password <code>password123</code>):</span>
          {DEMO.map((d) => (
            <button type="button" key={d} className="chip" onClick={() => { setEmail(d); setPassword('password123'); }}>
              {d}
            </button>
          ))}
        </div>
      </form>
    </div>
  );
}
