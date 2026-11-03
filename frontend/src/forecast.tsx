import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, jsonMutation } from './api'
import { date, message, money, QueryError, today } from './installments'

type Event = { date: string; source: string; sourceId?: string; name: string; amount: string; projected: boolean }
type Day = { date: string; income: string; ordinaryExpenses: string; installmentRepayments: string; openingPositions: string; endingBalance: string }
type Month = { month: string; income: string; ordinaryExpenses: string; installmentRepayments: string; openingPositions: string; endingBalance: string; lowestDailyBalance: string }
type ForecastResult = { startingBalance: string; endingBalance: string; lowestDailyBalance: string; events: Event[]; days: Day[]; months: Month[]; warning: string; actualThrough: string }
type ScenarioResult = { baseline: ForecastResult; hypothetical: ForecastResult; monthlyDeltas: { month: string; endingBalanceDelta: string; lowestBalanceDelta: string }[]; lowestBalanceDelta: string }
type Rule = { id: string; name: string; direction: string; active: boolean }

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

function MonthlyChart({ months }: { months: Month[] }) {
  const absolute = months.map(month => { const value = cents(month.endingBalance); return value < 0n ? -value : value })
  const max = absolute.reduce((a, b) => a > b ? a : b, 1n)
  return <div className="forecast-chart" role="img" aria-label="Monthly ending cash bars; exact values are in the table below">
    {months.map((month, index) => <div key={month.month} className="forecast-chart-row"><span>{month.month}</span><div className="forecast-chart-track"><div className={cents(month.endingBalance) < 0n ? 'forecast-chart-bar negative' : 'forecast-chart-bar'} style={{ width: `${Number(absolute[index] * 100n / max)}%` }}/></div><strong>{money(month.endingBalance)}</strong></div>)}
  </div>
}

function ForecastTable({ data, label }: { data: ForecastResult; label: string }) {
  return <><section className="panel forecast-summary" aria-label={label + ' summary'}>
    <div><span>Starting cash</span><strong>{money(data.startingBalance)}</strong></div>
    <div><span>Ending cash</span><strong>{money(data.endingBalance)}</strong></div>
    <div><span>Lowest daily cash</span><strong>{money(data.lowestDailyBalance)}</strong></div>
  </section><section className="panel forecast-section"><h2>{label} by month</h2><MonthlyChart months={data.months}/>
    <div className="table-wrap" tabIndex={0} role="region" aria-label={label + ' monthly forecast'}><table><thead><tr><th>Month</th><th>Income</th><th>Ordinary expenses</th><th>Installments</th><th>Opening positions</th><th>Ending cash</th><th>Lowest day</th></tr></thead><tbody>
      {data.months.map(m => <tr key={m.month}><th scope="row">{m.month}</th><td>{money(m.income)}</td><td>{money(m.ordinaryExpenses)}</td><td>{money(m.installmentRepayments)}</td><td>{money(m.openingPositions)}</td><td>{money(m.endingBalance)}</td><td>{money(m.lowestDailyBalance)}</td></tr>)}
    </tbody></table></div>
  </section><section className="panel forecast-section"><details><summary>Daily balances · {label}</summary><div className="table-wrap" tabIndex={0} role="region" aria-label={label + ' daily balances'}><table><thead><tr><th>Date</th><th>Income</th><th>Ordinary expenses</th><th>Installments</th><th>Opening positions</th><th>Ending cash</th></tr></thead><tbody>{data.days.map(day => <tr key={day.date}><th scope="row">{date(day.date)}</th><td>{money(day.income)}</td><td>{money(day.ordinaryExpenses)}</td><td>{money(day.installmentRepayments)}</td><td>{money(day.openingPositions)}</td><td>{money(day.endingBalance)}</td></tr>)}</tbody></table></div></details></section><section className="panel forecast-section"><h2>{label} sources</h2><p className="muted">Posted movements are marked actual. Future expectations do not post cash.</p>
    {data.events.length ? <ul className="record-list">{data.events.map((event, index) => <li key={`${event.source}-${event.sourceId || index}-${event.date}`} className="row"><span><strong>{event.name}</strong><br/><small>{date(event.date)} · {event.source.replaceAll('_', ' ')} · {event.projected ? 'Projected' : 'Actual / opening record'}</small></span><strong>{money(event.amount)}</strong></li>)}</ul> : <p>No itemized movements in this range. The balance may still include earlier actuals and opening positions.</p>}
  </section></>
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
    <section className="panel forecast-section"><h2>Forecast range</h2><div className="finance-form"><label>From<input type="date" value={from} onChange={event => { setFrom(event.target.value); setScenario(null) }}/></label><label>Through<input type="date" value={to} onChange={event => { setTo(event.target.value); setScenario(null) }}/></label></div><p className="muted">Choose up to 12 months, within 12 months of today. Days through today use posted actuals; later days use pending expectations.</p></section>
    {forecast.isPending ? <p role="status">Calculating cash flow…</p> : forecast.error ? <QueryError error={forecast.error} retry={() => forecast.refetch()}/> : forecast.data && <><p className="notice">{forecast.data.warning} Actuals included through {date(forecast.data.actualThrough)}.</p><ForecastTable data={forecast.data} label="Baseline"/></>}
    <section className="panel forecast-section"><h2>What if?</h2><p className="muted">Try one hypothetical change. No plan, payment, actual, or rule is saved.</p><form className="finance-form" onSubmit={simulate}>
      <label>Change type<select value={kind} onChange={event => { setKind(event.target.value); setScenario(null) }}><option value="ONE_TIME_EXPENSE">One-time expense</option><option value="NEW_INSTALLMENT">New fixed installment schedule</option><option value="INCOME_REDUCTION">Recurring income reduction</option></select></label>
      <label>Amount (EUR)<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" placeholder="0.00" required/></label>
      {kind === 'NEW_INSTALLMENT' ? <><label>First due date<input name="firstDueDate" type="date" min={from > today() ? from : today()} max={to} required/></label><label>Number of monthly installments<input name="installmentCount" type="number" min="1" max="600" required/></label><p className="muted wide">Amount is the value of each monthly installment. Dues beyond the selected range are omitted from this view.</p></> : <><label>{kind === 'INCOME_REDUCTION' ? 'Reduction starts' : 'Expense date'}<input name="date" type="date" min={from > today() ? from : today()} max={to} required/></label>{kind === 'INCOME_REDUCTION' && <label>Income source<select name="ruleId" required><option value="">Choose an active income rule</option>{rules.data?.filter(r => r.active && r.direction === 'INCOME').map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>}</>}
      <div className="wide"><button disabled={busy || !forecast.data}>{busy ? 'Comparing…' : 'Compare scenario'}</button></div>
    </form>{error && <p role="alert" className="error">{error}</p>}{rules.error && <p role="alert" className="error">{message(rules.error)}</p>}</section>
    {scenario && <><section className="panel forecast-section"><h2>Scenario difference</h2><p>Lowest daily balance change: <strong>{money(scenario.lowestBalanceDelta)}</strong></p><div className="table-wrap" tabIndex={0} role="region" aria-label="Monthly scenario differences"><table><thead><tr><th>Month</th><th>Ending cash change</th><th>Lowest day change</th></tr></thead><tbody>{scenario.monthlyDeltas.map(row => <tr key={row.month}><th scope="row">{row.month}</th><td>{money(row.endingBalanceDelta)}</td><td>{money(row.lowestBalanceDelta)}</td></tr>)}</tbody></table></div></section><ForecastTable data={scenario.hypothetical} label="What-if"/></>}
  </>
}
