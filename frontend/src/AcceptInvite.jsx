import { useState } from 'react';
import { api } from './api.js';

export default function AcceptInvite({ token }) {
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError('');
    try {
      setResult(await api.post('/invites/accept', { token, name, password }));
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="center">
      <form className="card narrow" onSubmit={submit}>
        <h2>Accept invite</h2>
        {result ? (
          <>
            <p>
              Done. {result.newAccount ? 'Your account was created.' : 'This invite was added to your existing account; use your current password.'}
            </p>
            <a href="/">Go to sign in</a>
          </>
        ) : (
          <>
            <p className="muted">If you already have an AgencyDesk account, your existing password is kept.</p>
            <label>Your name<input value={name} onChange={(e) => setName(e.target.value)} /></label>
            <label>Password (min 8)<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
            {error && <p className="error">{error}</p>}
            <button type="submit">Accept</button>
          </>
        )}
      </form>
    </div>
  );
}
