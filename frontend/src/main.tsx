import React, { useState, type FormEvent } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, NavLink, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, mutate, ApiError, type Session } from './api'
import './style.css'
import { Transactions, Planning, Categories } from './transactions'
import { Installments, CreatePlan, PlanDetail, Accounts } from './installments'
import { Forecast } from './forecast'
const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30000 } } })
const sections = ['Dashboard', 'Installments', 'Transactions', 'Forecast', 'Settings']
function App() {
  const cache = useQueryClient()
  const session = useQuery({ queryKey: ['session'], queryFn: () => api<Session>('/auth/me') })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    const data = new FormData(event.currentTarget)
    try {
      await mutate('/auth/login', new URLSearchParams({ username: String(data.get('email')), password: String(data.get('password')) }))
      await cache.invalidateQueries({ queryKey: ['session'] })
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to sign in.') }
    finally { setBusy(false) }
  }
  async function signOut() {
    setBusy(true); setError('')
    try { await mutate('/auth/logout'); cache.clear(); await session.refetch() }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to sign out.') }
    finally { setBusy(false) }
  }
  if (session.isPending) return <main className="auth" role="status">Opening your workspace…</main>
  if (!session.data) {
    const unavailable = session.error && (!(session.error instanceof ApiError) || session.error.status !== 401)
    return <main className="auth"><div className="brand">FT <span>Finance Tracker</span></div><h1>Your finances, in one place.</h1>
      <p>A private workspace for your financial records.</p>
      {unavailable ? <div role="alert"><p>The service is unavailable. Check that the backend and database are running.</p><button onClick={() => session.refetch()}>Try again</button></div> :
      <form onSubmit={signIn}><h2>Sign in</h2><label htmlFor="email">Email</label><input id="email" name="email" type="email" autoComplete="username" required maxLength={254}/>
      <label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete="current-password" required/>
      {error && <p role="alert" className="error">{error}</p>}<button disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      <p className="muted">First time? Configure the owner credentials in your local .env and start the backend. Public registration is disabled.</p></form>}</main>
  }
  return <div className="layout"><a className="skip" href="#content">Skip to content</a><aside><div className="brand">FT <span>Finance Tracker</span></div>
    <nav aria-label="Main navigation">{sections.map((name) => <NavLink key={name} to={'/' + name.toLowerCase()}>{name}</NavLink>)}</nav>
    <div className="owner"><span>{session.data.email}</span><button className="secondary" onClick={signOut} disabled={busy}>Sign out</button></div></aside>
    <main id="content" tabIndex={-1}>{error && <p role="alert" className="error">{error}</p>}
      <Routes><Route path="/" element={<Navigate to="/dashboard" replace/>}/><Route path="/installments" element={<Installments/>}/><Route path="/installments/new" element={<CreatePlan/>}/><Route path="/installments/:id" element={<PlanDetail/>}/><Route path="/transactions" element={<Transactions/>}/><Route path="/transactions/recurring" element={<Planning key="recurring" kind="recurring"/>}/><Route path="/transactions/planned" element={<Planning key="planned" kind="planned"/>}/><Route path="/forecast" element={<Forecast/>}/>{sections.filter(name => name !== 'Installments' && name !== 'Transactions' && name !== 'Forecast').map(name => <Route key={name} path={'/' + name.toLowerCase()} element={<Section name={name} email={session.data.email}/>}/>)}
      <Route path="*" element={<><h1>Page not found</h1><NavLink to="/dashboard">Go to dashboard</NavLink></>}/></Routes>
    </main></div>
}
function Section({ name, email }: { name: string; email: string }) {
  return <><header><p className="eyebrow">PERSONAL WORKSPACE · EUR</p><h1>{name}</h1><p className="muted">{name === 'Dashboard' ? 'A clear view starts with your own records.' : 'Your private financial workspace.'}</p></header>
    <div className="notice">Installments, actual transactions and cash-flow forecasts are available. Dashboard debt summaries remain planned.</div>
    {name === 'Settings' ? <section className="panel"><h2>Owner account</h2><dl><dt>Email</dt><dd>{email}</dd><dt>Currency</dt><dd>EUR</dd></dl><p>Session authentication is active. Account editing and password recovery are not available in this phase.</p></section> :
    <section className="panel empty"><div className="symbol" aria-hidden="true">{name === 'Installments' ? '↗' : '○'}</div><h2>{name === 'Dashboard' ? 'Your workspace is ready' : `${name} are planned`}</h2>
    <p>{name === 'Dashboard' ? 'Open Transactions for as-of cash balances and actual movement summaries, or Forecast for future cash flow. Dashboard debt totals and recent activity remain planned.' : 'This section is not available yet.'}</p>
    <span className="badge">Placeholder · No financial data</span></section>}
    {name === 'Settings' && <><Accounts/><Categories/></>}<p className="footnote">Manual entry · No bank connection · No analytics</p></>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={client}><BrowserRouter><App/></BrowserRouter></QueryClientProvider></React.StrictMode>)
