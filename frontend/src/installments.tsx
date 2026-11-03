import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, jsonMutation, ApiError } from './api'

type Money = string
type Account = { id: string; name: string; openingBalance: Money; openingDate: string }
type Due = { id: string; sequenceNumber: number; dueDate: string; amount: Money; remaining: Money; allocated: Money; status: string; partiallyPaid: boolean }
type Payment = { id: string; accountId: string; paidOn: string; amount: Money; note?: string; voidedAt?: string; allocations: { dueId: string; amount: Money }[] }
type Plan = { id: string; name: string; provider: string; description?: string; totalPayable: Money; totalPaid: Money; outstandingBalance: Money; percentagePaid: Money; nextDueDate?: string; overdueAmount: Money; projectedFinalDueDate: string; status: string; schedule: Due[]; payments: Payment[] }
type Page = { items: Plan[]; page: number; size: number; total: number }
export function money(value: Money) {
  const [whole, fraction = ''] = value.split('.')
  const parts = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).formatToParts(whole.startsWith('-') && BigInt(whole) === 0n ? -0 : BigInt(whole))
  return parts.map(p => p.type === 'fraction' ? fraction.padEnd(2, '0').slice(0, 2) : p.value).join('')
}
export function date(value?: string) { return value ? new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value + 'T12:00:00')) : '—' }
export function today() { return new Intl.DateTimeFormat('en-CA').format(new Date()) }
export function message(e: unknown) { return e instanceof ApiError && e.status === 401 ? 'Your session expired. Reload and sign in again.' : e instanceof Error ? e.message : 'Request failed. Please try again.' }
function value(data: FormData, name: string) { return String(data.get(name) ?? '') }
export function Amount({ name, label, signed = false, optional = false }: { name: string; label: string; signed?: boolean; optional?: boolean }) {
  return <label>{label}<input name={name} inputMode="decimal" pattern={signed ? '-?[0-9]+([.][0-9]{1,2})?' : '[0-9]+([.][0-9]{1,2})?'} placeholder="0.00" required={!optional} maxLength={21}/><small>EUR · use a decimal point, up to 2 decimal places</small></label>
}
export function Modal({ title, close, busy, children }: { title: string; close: () => void; busy: boolean; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => { const dialog = ref.current!; dialog.showModal(); return () => dialog.close() }, [])
  return <dialog ref={ref} className="modal" aria-labelledby={title} onCancel={e => { e.preventDefault(); if (!busy) close() }}>{children}</dialog>
}
export function QueryError({ error, retry }: { error: unknown; retry: () => void }) { return <div role="alert" className="error">{message(error)} <button onClick={retry}>Try again</button></div> }
function PlanCard({ plan }: { plan: Plan }) {
  return <Link className="plan-card" to={'/installments/' + plan.id}><div className="row"><div><h2>{plan.name}</h2><p className="muted">{plan.provider}</p></div><span className={'badge ' + plan.status.toLowerCase()}>{plan.status}</span></div>
    <p className="balance">{money(plan.outstandingBalance)} <span className="muted">outstanding</span></p>
    <progress max="100" value={plan.percentagePaid} aria-label={`${plan.name}: ${plan.percentagePaid}% paid`}/>
    <div className="row muted"><span>{money(plan.totalPaid)} paid of {money(plan.totalPayable)}</span><span>Next due {date(plan.nextDueDate)}</span></div></Link>
}
export function Installments() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const plans = useQuery({ queryKey: ['plans', page, search], queryFn: () => api<Page>(`/installments?page=${page}&size=12&search=${encodeURIComponent(search)}`) })
  return <><header className="row"><div><p className="eyebrow">FIXED MONTHLY COMMITMENTS · EUR</p><h1>Installments</h1><p className="muted">Track obligations and the payments you actually make.</p></div><Link className="button" to="/installments/new">Create plan</Link></header>
    <label className="search">Search plans<input type="search" value={search} onChange={e => { setSearch(e.target.value); setPage(0) }} maxLength={120}/></label>
    {plans.isPending ? <p role="status">Loading plans…</p> : plans.error ? <QueryError error={plans.error} retry={() => plans.refetch()}/> : plans.data?.items.length ? <div className="cards">{plans.data.items.map(p => <PlanCard key={p.id} plan={p}/>)}</div> : <section className="panel empty"><h2>{search ? 'No matching plans' : 'No installment plans yet'}</h2><p>Create a plan to generate its monthly schedule. Payments are recorded separately from the amounts due.</p></section>}
    {plans.data && plans.data.total > 12 && <div className="row pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * 12 >= plans.data.total} onClick={() => setPage(page + 1)}>Next</button></div>}
  </>
}
export function CreatePlan() {
  const navigate = useNavigate(); const cache = useQueryClient()
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const data = new FormData(e.currentTarget); setError(''); setBusy(true)
    try {
      const plan = await jsonMutation<Plan>('/installments', { name: value(data, 'name'), provider: value(data, 'provider'), description: value(data, 'description'), principalAmount: value(data, 'principalAmount') || null, totalPayable: value(data, 'totalPayable'), installmentCount: Number(value(data, 'installmentCount')), firstDueDate: value(data, 'firstDueDate'), currency: 'EUR' })
      await cache.invalidateQueries({ queryKey: ['plans'] }); navigate('/installments/' + plan.id)
    } catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  return <><Link to="/installments">← All plans</Link><h1>Create installment plan</h1><p className="muted">Fixed total payable in EUR. Monthly dues keep the original day, clamped to shorter months. The final due receives any remaining cents.</p>
    <form className="panel finance-form" onSubmit={submit}><label>Plan name<input name="name" required maxLength={120}/></label><label>Provider / merchant<input name="provider" required maxLength={120}/></label>
    <Amount name="totalPayable" label="Total contractual payable"/><Amount name="principalAmount" label="Original principal (optional)" optional/>
    <label>Number of monthly installments<input name="installmentCount" type="number" min="1" max="600" step="1" required/></label><label>First due date<input name="firstDueDate" type="date" required/></label>
    <label className="wide">Description (optional)<textarea name="description" maxLength={2000}/></label><p className="notice wide">The schedule cannot be edited. Check the total and first due date before creating this plan.</p>
    {error && <p className="error wide" role="alert">{error}</p>}<div className="row wide"><button disabled={busy}>{busy ? 'Creating…' : 'Create plan'}</button><Link to="/installments">Cancel</Link></div></form></>
}
export function Accounts() {
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') }); const cache = useQueryClient()
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const data = new FormData(form); setError(''); setSuccess(''); setBusy(true)
    try { await jsonMutation('/accounts', { name: value(data, 'name'), openingBalance: value(data, 'openingBalance'), openingDate: value(data, 'openingDate') }); await Promise.all([cache.invalidateQueries({ queryKey: ['accounts'] }), cache.invalidateQueries({ queryKey: ['cash-summary'] }), cache.invalidateQueries({ queryKey: ['balances'] })]); form.reset(); setSuccess('Cash account created.') }
    catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  return <section className="panel accounts"><h2>Cash accounts</h2><p className="muted">Set an accurate opening position before recording payments. These are opening balances, not current bank balances. Accounts cannot yet be edited.</p>
    {accounts.isPending ? <p role="status">Loading accounts…</p> : accounts.error ? <QueryError error={accounts.error} retry={() => accounts.refetch()}/> : accounts.data?.length ? <ul className="account-list">{accounts.data.map(a => <li key={a.id}><strong>{a.name}</strong><span>Opened {date(a.openingDate)} · {money(a.openingBalance)}</span></li>)}</ul> : <p>No cash accounts yet.</p>}
    <form className="finance-form" onSubmit={submit}><label>Account name<input name="name" required maxLength={120}/></label><Amount name="openingBalance" label="Opening balance" signed/>
    <label>Opening date<input name="openingDate" type="date" max={today()} required/></label>{error && <p role="alert" className="error wide">{error}</p>}{success && <p role="status" className="success wide">{success}</p>}<div className="wide"><button disabled={busy}>{busy ? 'Creating…' : 'Add cash account'}</button></div></form></section>
}
export function PlanDetail() {
  const { id } = useParams(); const cache = useQueryClient()
  const plan = useQuery({ queryKey: ['plan', id], queryFn: () => api<Plan>('/installments/' + id) })
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') })
  const [paying, setPaying] = useState(false); const [voiding, setVoiding] = useState<Payment | null>(null)
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [busy, setBusy] = useState(false)
  const pending = useRef<{ key: string; body: string } | null>(null)
  async function refresh() { await Promise.all([cache.invalidateQueries({ queryKey: ['plan', id] }), cache.invalidateQueries({ queryKey: ['plans'] }), cache.invalidateQueries({ queryKey: ['cash-summary'] }), cache.invalidateQueries({ queryKey: ['balances'] })]) }
  async function pay(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const data = new FormData(e.currentTarget); const body = { accountId: value(data, 'accountId'), amount: value(data, 'amount'), paidOn: value(data, 'paidOn'), note: value(data, 'note') }
    const encoded = JSON.stringify(body)
    if (!pending.current || pending.current.body !== encoded) pending.current = { body: encoded, key: crypto.randomUUID() }
    setBusy(true); setError(''); setSuccess('')
    try { await jsonMutation(`/installments/${id}/payments`, body, pending.current.key); pending.current = null; setPaying(false); setSuccess('Payment recorded. Outstanding amounts have been recalculated.'); await refresh() }
    catch (e) { setError(message(e) + ' If the response was interrupted, retry unchanged to reuse the same request key.') } finally { setBusy(false) }
  }
  async function reverse() {
    setBusy(true); setError(''); setSuccess('')
    try { await jsonMutation(`/installments/${id}/payments/${voiding!.id}/void`); setVoiding(null); setSuccess('Payment voided. History is retained.'); await refresh() }
    catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  if (plan.isPending) return <p role="status">Loading plan…</p>
  if (plan.error) return <QueryError error={plan.error} retry={() => plan.refetch()}/>
  const p = plan.data!
  return <><Link to="/installments">← All plans</Link><header className="row"><div><h1>{p.name}</h1><p className="muted">{p.provider} · {p.status}</p></div><button disabled={p.status === 'PAID'} onClick={() => { setPaying(true); setError('') }}>Record payment</button></header>
    {p.description && <p>{p.description}</p>}{success && <p role="status" className="success">{success}</p>}{error && !paying && !voiding && <p role="alert" className="error">{error}</p>}
    <div className="metrics"><section className="panel"><p className="muted">Outstanding</p><strong>{money(p.outstandingBalance)}</strong></section><section className="panel"><p className="muted">Paid / total</p><strong>{money(p.totalPaid)} / {money(p.totalPayable)}</strong></section><section className="panel"><p className="muted">Overdue</p><strong>{money(p.overdueAmount)}</strong></section></div>
    <div className="panel progress-panel"><label>Paid: {p.percentagePaid.replace('.', ',')}%<progress max="100" value={p.percentagePaid}/></label><div className="row muted"><span>Next unpaid due: {date(p.nextDueDate)}</span><span>Scheduled final due: {date(p.projectedFinalDueDate)}</span></div></div>
    <section className="panel"><h2>Monthly obligations</h2><p className="muted">Dues are obligations. Only posted payments count as actual cash movement.</p><div className="table-wrap" tabIndex={0} role="region" aria-label="Monthly installment schedule"><table><thead><tr><th>Due</th><th>Date</th><th>Amount</th><th>Remaining</th><th>Status</th></tr></thead><tbody>{p.schedule.map(d => <tr key={d.id}><td>{d.sequenceNumber}</td><td>{date(d.dueDate)}</td><td>{money(d.amount)}</td><td>{money(d.remaining)}</td><td>{d.status}{d.partiallyPaid && d.status === 'OVERDUE' && ' · Partially paid'}</td></tr>)}</tbody></table></div></section>
    <section className="panel history"><h2>Payment history</h2>{p.payments.length ? <ul>{p.payments.map(payment => <li key={payment.id}><div className="row"><div><strong>{money(payment.amount)} · {date(payment.paidOn)}</strong><p>{accounts.data?.find(a => a.id === payment.accountId)?.name || 'Cash account'}{payment.voidedAt && ' · VOIDED'}</p></div>{!payment.voidedAt && <button className="secondary" onClick={() => { setVoiding(payment); setError('') }}>Void payment</button>}</div>{payment.note && <p>{payment.note}</p>}<p className="muted">{payment.allocations.map(a => `Due ${p.schedule.find(d => d.id === a.dueId)?.sequenceNumber}: ${money(a.amount)}`).join(' · ')}</p></li>)}</ul> : <p className="muted">No payments recorded yet.</p>}</section>
    {paying && <Modal title="payment-title" close={() => { setPaying(false); setError('') }} busy={busy}><form onSubmit={pay}><h2 id="payment-title">Record actual payment</h2><p>Outstanding: {money(p.outstandingBalance)}</p>
    {accounts.isPending ? <p role="status">Loading accounts…</p> : accounts.error ? <QueryError error={accounts.error} retry={() => accounts.refetch()}/> : !accounts.data?.length ? <p>Create a cash account in <Link to="/settings">Settings</Link> first.</p> : <><label>Cash account<select name="accountId" required>{accounts.data.map(a => <option key={a.id} value={a.id}>{a.name} · opened {date(a.openingDate)}</option>)}</select></label><Amount name="amount" label="Payment amount"/><label>Payment date<input name="paidOn" type="date" max={today()} defaultValue={today()} required/></label><label>Note (optional)<textarea name="note" maxLength={2000}/></label></>}
    {error && <p role="alert" className="error">{error}</p>}<div className="row"><button disabled={busy || !accounts.data?.length}>{busy ? 'Recording…' : 'Record payment'}</button><button type="button" className="secondary" disabled={busy} onClick={() => { setPaying(false); setError('') }}>Cancel</button></div></form></Modal>}
    {voiding && <Modal title="void-title" close={() => { setVoiding(null); setError('') }} busy={busy}><h2 id="void-title">Void this payment?</h2><p>{money(voiding.amount)} on {date(voiding.paidOn)} will stop reducing the outstanding balance. The original payment and allocations stay in history.</p>{error && <p role="alert" className="error">{error}</p>}<div className="row"><button disabled={busy} onClick={reverse}>{busy ? 'Voiding…' : 'Confirm void'}</button><button className="secondary" disabled={busy} onClick={() => { setVoiding(null); setError('') }}>Cancel</button></div></Modal>}
  </>
}
