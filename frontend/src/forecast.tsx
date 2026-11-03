import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, jsonMutation } from './api'
import { date, message, money, QueryError, today } from './installments'

type Event = { date: string; source: string; sourceId?: string; name: string; amount: string; projected: boolean }
type Day = { date: string; income: string; ordinaryExpenses: string; installmentRepayments: string; openingPositions: string; endingBalance: string; debtOutstanding: string }
type Month = { month: string; income: string; ordinaryExpenses: string; installmentRepayments: string; openingPositions: string; endingBalance: string; lowestDailyBalance: string; debtOutstanding: string }
type ForecastResult = { startingBalance: string; endingBalance: string; lowestDailyBalance: string; startingDebt: string; endingDebt: string; accountBalances: { accountId: string; name: string; currentBalance: string }[]; events: Event[]; days: Day[]; months: Month[]; warning: string; actualThrough: string }
type ScenarioResult = { baseline: ForecastResult; hypothetical: ForecastResult; monthlyDeltas: { month: string; endingBalanceDelta: string; lowestBalanceDelta: string }[]; lowestBalanceDelta: string }
type Rule = { id: string; name: string; direction: string; active: boolean }
type Category = { id: string; name: string; kind: string }
type Account = { id: string; name: string }

function endAfter(months: number) {
  const current = new Date(today() + 'T12:00:00')
  return new Date(current.getFullYear(), current.getMonth() + months, 0, 12).toLocaleDateString('en-CA')
}

function cents(value: string) {
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = value.replace('-', '').split('.')
  const total = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
  return negative ? -total : total
}

function ProgressChart({ months }: { months: Month[] }) {
  const values = months.flatMap(month => [cents(month.endingBalance), cents(month.debtOutstanding)])
  const max = values.reduce((largest, value) => { const absolute = value < 0n ? -value : value; return absolute > largest ? absolute : largest }, 1n)
  const width = (value: string) => { const amount = cents(value); return `${Number((amount < 0n ? -amount : amount) * 100n / max)}%` }
  return <div className="forecast-chart" role="img" aria-label="Monthly cash across all accounts and remaining installment debt; exact values are in the table below">
    <p className="muted">Cash across all accounts <span className="chart-key cash"/> · Remaining installment debt <span className="chart-key debt"/></p>
    {months.map(month => <div key={month.month} className="forecast-chart-month"><strong>{month.month}</strong><div className="forecast-chart-row"><span>Cash</span><div className="forecast-chart-track"><div className={cents(month.endingBalance) < 0n ? 'forecast-chart-bar negative' : 'forecast-chart-bar'} style={{ width: width(month.endingBalance) }}/></div><strong>{money(month.endingBalance)}</strong></div><div className="forecast-chart-row"><span>Debt</span><div className="forecast-chart-track"><div className="forecast-chart-bar debt" style={{ width: width(month.debtOutstanding) }}/></div><strong>{money(month.debtOutstanding)}</strong></div></div>)}
  </div>
}

function ForecastTable({ data, label }: { data: ForecastResult; label: string }) {
  return <><section className="panel forecast-summary" aria-label={label + ' summary'}>
    <div><span>Starting cash</span><strong>{money(data.startingBalance)}</strong></div>
    <div><span>Ending cash</span><strong>{money(data.endingBalance)}</strong></div>
    <div><span>Lowest daily cash</span><strong>{money(data.lowestDailyBalance)}</strong></div>
    <div><span>Starting debt</span><strong>{money(data.startingDebt)}</strong></div>
    <div><span>Ending debt</span><strong>{money(data.endingDebt)}</strong></div>
  </section><section className="panel forecast-section"><h2>{label} cash and debt by month</h2><p className="muted">Future dues are assumed paid on their projected dates for this graph; only recorded payments have actually reduced debt.</p><ProgressChart months={data.months}/>
    <div className="table-wrap" tabIndex={0} role="region" aria-label={label + ' monthly forecast'}><table><thead><tr><th>Month</th><th>Income</th><th>Ordinary expenses</th><th>Installments</th><th>Opening positions</th><th>Ending cash</th><th>Lowest day</th><th>Remaining debt</th></tr></thead><tbody>
      {data.months.map(m => <tr key={m.month}><th scope="row">{m.month}</th><td>{money(m.income)}</td><td>{money(m.ordinaryExpenses)}</td><td>{money(m.installmentRepayments)}</td><td>{money(m.openingPositions)}</td><td>{money(m.endingBalance)}</td><td>{money(m.lowestDailyBalance)}</td><td>{money(m.debtOutstanding)}</td></tr>)}
    </tbody></table></div>
  </section><section className="panel forecast-section"><details><summary>Daily balances · {label}</summary><div className="table-wrap" tabIndex={0} role="region" aria-label={label + ' daily balances'}><table><thead><tr><th>Date</th><th>Income</th><th>Ordinary expenses</th><th>Installments</th><th>Opening positions</th><th>Ending cash</th><th>Remaining debt</th></tr></thead><tbody>{data.days.map(day => <tr key={day.date}><th scope="row">{date(day.date)}</th><td>{money(day.income)}</td><td>{money(day.ordinaryExpenses)}</td><td>{money(day.installmentRepayments)}</td><td>{money(day.openingPositions)}</td><td>{money(day.endingBalance)}</td><td>{money(day.debtOutstanding)}</td></tr>)}</tbody></table></div></details></section><section className="panel forecast-section"><h2>{label} sources</h2><p className="muted">Posted movements are marked actual. Future expectations do not post cash.</p>
    {data.events.length ? <ul className="record-list">{data.events.map((event, index) => <li key={`${event.source}-${event.sourceId || index}-${event.date}`} className="row"><span><strong>{event.name}</strong><br/><small>{date(event.date)} · {event.source.replaceAll('_', ' ')} · {event.projected ? 'Projected' : 'Actual / opening record'}</small></span><strong>{money(event.amount)}</strong></li>)}</ul> : <p>No itemized movements in this range. The balance may still include earlier actuals and opening positions.</p>}
  </section></>
}

function ScheduledSourceForm({ onCreated }: { onCreated: () => void }) {
  const cache = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('/categories') })
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') })
  const [direction, setDirection] = useState('INCOME'), [schedule, setSchedule] = useState('MONTHLY')
  const [error, setError] = useState(''), [success, setSuccess] = useState(''), [busy, setBusy] = useState(false)
  const retry = useRef<{ fingerprint: string; key: string } | null>(null)
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form)
    const common = { name: String(data.get('name')), accountId: String(data.get('accountId')) || null,
      categoryId: String(data.get('categoryId')), direction, amount: String(data.get('amount')) }
    const planned = schedule === 'ONE_OFF'
    const path = planned ? '/planned-cashflows' : '/recurring-rules'
    const body = planned ? { ...common, expectedDate: String(data.get('date')) }
      : { ...common, frequency: schedule, anchorDate: String(data.get('date')), startDate: String(data.get('date')), endDate: String(data.get('endDate')) || null }
    const fingerprint = JSON.stringify([path, body])
    if (!retry.current || retry.current.fingerprint !== fingerprint) retry.current = { fingerprint, key: crypto.randomUUID() }
    setBusy(true); setError(''); setSuccess('')
    try {
      await jsonMutation(path, body, retry.current.key)
      retry.current = null
      await Promise.all(['forecasts', 'rules', 'planned'].map(key => cache.invalidateQueries({ queryKey: [key] })))
      onCreated(); form.reset(); setDirection('INCOME'); setSchedule('MONTHLY')
      setSuccess(planned ? 'One-off expectation added. No actual cash posted.' : 'Recurring expectation added. No actual cash posted.')
    } catch (cause) { setError(message(cause) + ' Retry unchanged to reuse the request key.') }
    finally { setBusy(false) }
  }
  return <section className="panel forecast-section"><h2>Add expected income or expense</h2>
    <p className="muted">Choose a descriptive category and how often it occurs. These entries affect the forecast only; record received income or paid expenses as actuals in Transactions.</p>
    <form className="finance-form" onSubmit={create}>
      <label>Source name<input name="name" required maxLength={120} placeholder="Salary, rent, groceries…"/></label>
      <label>Income or expense<select value={direction} onChange={event => setDirection(event.target.value)}><option value="INCOME">Income</option><option value="EXPENSE">Expense</option></select></label>
      <label>Category<select name="categoryId" key={direction} required defaultValue=""><option value="">Choose {direction.toLowerCase()} category</option>{categories.data?.filter(category => category.kind === direction).map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
      <label>Cash account (optional)<select name="accountId"><option value="">All-account expectation</option>{accounts.data?.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select></label>
      <label>Schedule<select value={schedule} onChange={event => setSchedule(event.target.value)}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option><option value="ONE_OFF">One-off</option></select></label>
      <label>Amount (EUR)<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" placeholder="0.00" required maxLength={20}/></label>
      <label>{schedule === 'ONE_OFF' ? 'Expected date' : 'First expected date / anchor'}<input name="date" type="date" min={new Date(Date.parse(today() + 'T12:00:00') + 86400000).toLocaleDateString('en-CA')} required/></label>
      {schedule !== 'ONE_OFF' && <label>End date (optional)<input name="endDate" type="date"/></label>}
      <div className="wide"><button disabled={busy || !categories.data?.some(category => category.kind === direction)}>{busy ? 'Adding…' : 'Add expected item'}</button></div>
    </form>
    {error && <p role="alert" className="error">{error}</p>}{success && <p role="status" className="success">{success}</p>}
    {(categories.error || accounts.error) && <p role="alert" className="error">{message(categories.error || accounts.error)}</p>}
    <p className="muted">Need a category? Add one in <Link to="/settings">Settings</Link>. Manage or pause recurring rules in <Link to="/transactions/recurring">Transactions</Link>.</p>
  </section>
}

export function Forecast() {
  const [from, setFrom] = useState(today()), [to, setTo] = useState(endAfter(6))
  const [kind, setKind] = useState('ONE_TIME_EXPENSE')
  const [scenario, setScenario] = useState<ScenarioResult | null>(null)
  const [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const params = new URLSearchParams({ from, to })
  const forecast = useQuery({ queryKey: ['forecasts', params.toString()], enabled: !!from && !!to && from <= to,
    queryFn: () => api<ForecastResult>('/forecasts/cashflow?' + params) })
  const rules = useQuery({ queryKey: ['rules'], queryFn: () => api<Rule[]>('/recurring-rules') })
  async function simulate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setScenario(null)
    const data = new FormData(event.currentTarget)
    const amount = String(data.get('amount'))
    const change = kind === 'NEW_INSTALLMENT'
      ? { type: kind, amount, firstDueDate: String(data.get('firstDueDate')), installmentCount: Number(data.get('installmentCount')) }
      : kind === 'INCOME_REDUCTION'
      ? { type: kind, amount, date: String(data.get('date')), ruleId: String(data.get('ruleId')) }
      : { type: kind, amount, date: String(data.get('date')) }
    try { setScenario(await jsonMutation<ScenarioResult>('/forecasts/scenarios', { from, to, changes: [change] })) }
    catch (cause) { setError(message(cause)) }
    finally { setBusy(false) }
  }
  return <><header><p className="eyebrow">DETERMINISTIC CASH FLOW · EUR</p><h1>Forecast</h1><p className="muted">Inspect future expectations alongside posted activity. Simulations are read-only.</p></header>
    <p className="notice">Income sources come from <Link to="/transactions/recurring">recurring income rules</Link> and <Link to="/transactions/planned">planned income</Link>. Record received income in <Link to="/transactions">Transactions</Link> and link it to its source to avoid projecting it again.</p>
    <ScheduledSourceForm onCreated={() => setScenario(null)}/>
    <section className="panel forecast-section"><h2>Forecast range</h2><div className="finance-form"><label>From<input type="date" value={from} onChange={event => { setFrom(event.target.value); setScenario(null) }}/></label><label>Through<input type="date" value={to} onChange={event => { setTo(event.target.value); setScenario(null) }}/></label></div><p className="muted">Choose up to 12 months, within 12 months of today. Days through today use posted actuals; later days use pending expectations.</p></section>
    {forecast.isPending ? <p role="status">Calculating cash flow…</p> : forecast.error ? <QueryError error={forecast.error} retry={() => forecast.refetch()}/> : forecast.data && <><p className="notice">{forecast.data.warning} Actuals included through {date(forecast.data.actualThrough)}.</p><section className="panel forecast-section"><h2>Current cash by account</h2><p className="muted">Posted actual balance as of today. The graph below combines all accounts and expected future cash flow.</p>{forecast.data.accountBalances.length ? <ul className="account-list">{forecast.data.accountBalances.map(account => <li key={account.accountId}><strong>{account.name}</strong><span>{money(account.currentBalance)}</span></li>)}</ul> : <p>No cash accounts yet. Add one in <Link to="/settings">Settings</Link>.</p>}</section><ForecastTable data={forecast.data} label="Baseline"/></>}
    <section className="panel forecast-section"><h2>What if?</h2><p className="muted">Try one hypothetical change. No plan, payment, actual, or rule is saved.</p><form className="finance-form" onSubmit={simulate}>
      <label>Change type<select value={kind} onChange={event => { setKind(event.target.value); setScenario(null) }}><option value="ONE_TIME_EXPENSE">One-time expense</option><option value="NEW_INSTALLMENT">New fixed installment schedule</option><option value="INCOME_REDUCTION">Recurring income reduction</option></select></label>
      <label>Amount (EUR)<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" placeholder="0.00" required/></label>
      {kind === 'NEW_INSTALLMENT' ? <><label>First due date<input name="firstDueDate" type="date" min={from > today() ? from : today()} max={to} required/></label><label>Number of monthly installments<input name="installmentCount" type="number" min="1" max="600" required/></label><p className="muted wide">Amount is the value of each monthly installment. Dues beyond the selected range are omitted from this view.</p></> : <><label>{kind === 'INCOME_REDUCTION' ? 'Reduction starts' : 'Expense date'}<input name="date" type="date" min={from > today() ? from : today()} max={to} required/></label>{kind === 'INCOME_REDUCTION' && <label>Income source<select name="ruleId" required><option value="">Choose an active income rule</option>{rules.data?.filter(r => r.active && r.direction === 'INCOME').map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>}</>}
      <div className="wide"><button disabled={busy || !forecast.data}>{busy ? 'Comparing…' : 'Compare scenario'}</button></div>
    </form>{error && <p role="alert" className="error">{error}</p>}{rules.error && <p role="alert" className="error">{message(rules.error)}</p>}</section>
    {scenario && <><section className="panel forecast-section"><h2>Scenario difference</h2><p>Lowest daily balance change: <strong>{money(scenario.lowestBalanceDelta)}</strong></p><div className="table-wrap" tabIndex={0} role="region" aria-label="Monthly scenario differences"><table><thead><tr><th>Month</th><th>Ending cash change</th><th>Lowest day change</th></tr></thead><tbody>{scenario.monthlyDeltas.map(row => <tr key={row.month}><th scope="row">{row.month}</th><td>{money(row.endingBalanceDelta)}</td><td>{money(row.lowestBalanceDelta)}</td></tr>)}</tbody></table></div></section><ForecastTable data={scenario.hypothetical} label="What-if"/></>}
  </>
}
