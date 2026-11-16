import Link from "next/link";
export default function HomePage(){
 return <div className="shell">
  <section className="hero"><div className="eyebrow"><span className="pulse"/> CLIENT ECOSYSTEM / 1.21.11</div>
   <h1>Твой мир.<br/><span>Твои правила.</span></h1>
   <p className="lead">Единый аккаунт PROJECT VISUAL для Minecraft-клиента, друзей, совместных мини-игр и косметики.</p>
   <div className="actions"><Link className="button primary" href="/register">Создать аккаунт</Link><Link className="button ghost" href="/login">У меня есть аккаунт</Link></div>
   <div className="hero-note">Сайт находится в разработке. Первая версия не опубликована.</div>
  </section>
  <section className="feature-grid">
   <article className="feature"><div className="feature-icon">01</div><h2>Один аккаунт</h2><p>Подтверди почту и привяжи PROJECT VISUAL в Minecraft одноразовым кодом.</p></article>
   <article className="feature"><div className="feature-icon">02</div><h2>Друзья онлайн</h2><p>Добавляй игроков PROJECT VISUAL, смотри статус и отправляй приглашения на матчи.</p></article>
   <article className="feature"><div className="feature-icon">03</div><h2>Косметика</h2><p>Инвентарь скинов будет синхронизироваться с клиентом. Шансы кейсов открыты заранее.</p></article>
  </section>
  <section className="wide-card"><div><div className="eyebrow">SOCIAL PLAY</div><h2>Шахматы и крестики-нолики — вместе</h2><p>Матчи хранятся на сервере аккаунтов, чтобы игроки видели одинаковые ходы и результат.</p></div>
   <div className="game-preview"><span>♟</span><span>×</span><span>○</span><span>♞</span><small>PLAY WITH FRIENDS</small></div>
  </section>
 </div>;
}
