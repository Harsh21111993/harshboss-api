-- ============================================================================
-- Harsh-Boss — seed data (V2)
-- Reproduces the 12 prototype emails + 12 calendar events + 1 pending approval
-- All timestamps are relative to NOW() so the demo always looks "live".
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 12 emails (inbox + spam) — same content as the Next.js prototype
-- ---------------------------------------------------------------------------
INSERT INTO emails (from_address, from_name, subject, body, folder, is_read, received_at) VALUES

-- 1. inbox / unread — Sarah Chen — Q3 budget approval (URGENT)
('sarah.chen@acme-corp.com', 'Sarah Chen', 'Q3 Budget Approval — need your sign-off by Friday',
 'Hi,\n\nAttached is the Q3 departmental budget for your review. Marketing''s paid-media line item jumped 18% and Finance flagged it. I need your sign-off by end of day Friday so we can lock the numbers before the board readout on Monday.\n\nHappy to walk you through the variances — I''m free Thursday after 2pm.\n\nThanks,\nSarah',
 'INBOX', FALSE, NOW() - interval '28 minutes'),

-- 2. inbox / unread — Teams no-reply — missed Sprint Review
('noreply@teams.com', 'Microsoft Teams', 'You missed a meeting: Sprint Review',
 'You missed a meeting that was scheduled in Microsoft Teams.\n\n• Meeting: Sprint Review — Q2 Cycle 6\n• When: Yesterday, 4:00 PM – 4:45 PM\n• Organizer: raj.patel@globalpay.io\n\nThe meeting recording and transcript are available in the channel. Action items have been posted to the Sprint Planning board.',
 'INBOX', FALSE, NOW() - interval '3 hours'),

-- 3. inbox / read — newsletter — 12 PM frameworks
('newsletter@producttea.co', 'ProductTea Weekly', 'This week in Product: 12 frameworks every PM should know',
 'Hey product people 👋\n\nThis week we breakdown 12 decision-making frameworks every PM should have in their toolkit — from RICE and Kano to Wardley Maps and Pre-mortems. Plus: why Linear''s planning philosophy is quietly winning.\n\nRead time: 7 minutes.\n\n— The ProductTea team',
 'INBOX', TRUE, NOW() - interval '6 hours'),

-- 4. inbox / unread — Raj Patel — Production incident P1
('raj.patel@globalpay.io', 'Raj Patel', 'Production incident: payment-service P1 — your on-call',
 'Aarav,\n\nP1 on payment-service. ~3% of card authorizations are returning 503s since 11:42 UTC. We''ve failed over to the secondary region but the queue is backing up. You''re the on-call IC — can you join the incident bridge ASAP?\n\nBridge: teams.microsoft.com/l/meetup-bridge/inc-2041\nStatus page is updated; legal is on standby.\n\nRaj',
 'INBOX', FALSE, NOW() - interval '47 minutes'),

-- 5. spam / unread — lottery winner
('lottery-winner@claim-prize.tk', 'Prize Claims Dept.', 'CONGRATULATIONS YOU WON $2,000,000',
 'DEAR LUCKY WINNER,\n\nYour email address has been selected in the INTERNATIONAL MEGA JACKPOT PROMOTION. You have won USD $2,000,000.00 (TWO MILLION DOLLARS).\n\nTo claim your prize, please send your full name, address, phone number, and a processing fee of $250 USD via Western Union to our claims agent within 48 hours.\n\nCongratulations once again!\n\nMr. Daniel Okoye\nClaims Director',
 'SPAM', FALSE, NOW() - interval '5 hours'),

-- 6. spam / unread — Emily Watson — partnership follow-up (THE BURIED IMPORTANT)
('emily.watson@vertex-partners.com', 'Emily Watson', 'Re: Partnership proposal — following up',
 'Hi Aarav,\n\nFollowing up on the partnership proposal I sent over last month — Vertex Partners is still very interested in co-developing an integration between Harsh-Boss and our workflow engine. We''ve had three inbound enterprise customers ask for exactly this in the last two weeks.\n\nI know Q2 was busy. Could we grab 30 minutes next week to discuss scoping? I''m happy to send a one-pager in advance.\n\nBest,\nEmily Watson\nVP, Strategic Partnerships — Vertex',
 'SPAM', FALSE, NOW() - interval '4 hours'),

-- 7. inbox / unread — GitHub — PR needs review
('noreply@github.com', 'GitHub', '[acme-platform] PR #1247 needs your review',
 'A pull request requires your review.\n\nRepository: acme-platform/api-gateway\nPR #1247: refactor: extract auth middleware into standalone module\nAuthor: kelly.morgan@acme-corp.com\nFiles changed: 14 (+312, -198)\n\nDescription: Splits the monolithic auth middleware into a composable module and adds integration tests for the JWT rotation path. Targeting the v3.2 release branch.\n\nView PR: github.com/acme-platform/api-gateway/pull/1247',
 'INBOX', FALSE, NOW() - interval '1 hour 20 minutes'),

-- 8. inbox / read — HR — updated employee handbook
('hr@acme-corp.com', 'ACME People Ops', 'Updated employee handbook 2025',
 'Hello team,\n\nThe 2025 employee handbook is now available. Key updates this year:\n\n• Refreshed remote-work policy (now 3 days in office, 2 remote)\n• Expanded parental leave to 16 weeks\n• Updated expense guidelines for travel\n\nPlease review by end of month and acknowledge in Workday.\n\nThanks,\nACME People Ops',
 'INBOX', TRUE, NOW() - interval '1 day'),

-- 9. inbox / unread — Stripe — invoice paid
('receipts@stripe.com', 'Stripe', 'Invoice #INV-2025-0892 paid — $12,400',
 'You received a payment.\n\n• Invoice: INV-2025-0892\n• Customer: Northwind Retail Inc.\n• Amount: $12,400.00 USD\n• Paid on: today\n\nFunds will arrive in your bank account within 2 business days.\n\nView invoice: dashboard.stripe.com/invoices/INV-2025-0892',
 'INBOX', FALSE, NOW() - interval '2 hours 10 minutes'),

-- 10. inbox / unread — Mom — dinner Sunday
('patel.family@gmail.com', 'Mom', 'Dinner this Sunday?',
 'Beta,\n\nAre you coming over for dinner this Sunday? Your dad is making biryani and Priya is in town for the weekend. Come around 7pm — and bring laundry if you have any, you know I''ll do it anyway 😊\n\nLove you,\nMom',
 'INBOX', FALSE, NOW() - interval '1 hour 45 minutes'),

-- 11. inbox / read — LinkedIn — 14 searches
('noreply@linkedin.com', 'LinkedIn', 'You appeared in 14 searches this week',
 'Hi Aarav,\n\nYou appeared in 14 searches this week. Most people who found you work at:\n\n• Stripe\n• Datadog\n• Ramp\n\nWant to see who searched for you? Upgrade to Premium.',
 'INBOX', TRUE, NOW() - interval '8 hours'),

-- 12. spam / unread — crypto moonshot
('tips@cryptomoonshot.xyz', 'Crypto Moonshot Insider', '1000x coin insider tip — join now',
 '🚀 INSIDER ALERT 🚀\n\nOur analysts have identified a low-cap gem that''s about to 1000x in the next 72 hours. This is the same call that made our premium members 8,400% on $PEPE last cycle.\n\nJoin our VIP Telegram group for the ticker — only 50 spots left, entry closes tonight!\n\nDisclaimer: not financial advice. Dyor.',
 'SPAM', FALSE, NOW() - interval '7 hours');

-- ---------------------------------------------------------------------------
-- 12 calendar events across the next 7 days
-- ---------------------------------------------------------------------------
INSERT INTO calendar_events
  (title, platform, start_time, end_time, organizer, attendees, join_url, location, is_hidden_by_others, status)
VALUES

-- Today
('Teams Daily Standup', 'TEAMS',
 date_trunc('day', NOW()) + interval '9 hours 30 minutes',
 date_trunc('day', NOW()) + interval '10 hours',
 'Dana White',
 '["Dana White","Aarav Patel","Kelly Morgan","Raj Patel","Mei Lin"]',
 'https://teams.microsoft.com/l/meetup-bridge/standup-930',
 NULL, FALSE, 'CONFIRMED'),

('1:1 with Dana (Manager)', 'GOOGLE',
 date_trunc('day', NOW()) + interval '11 hours',
 date_trunc('day', NOW()) + interval '11 hours 30 minutes',
 'Dana White',
 '["Dana White","Aarav Patel"]',
 'https://meet.google.com/abc-defg-hij',
 NULL, FALSE, 'CONFIRMED'),

('Customer Demo — Northwind Retail', 'ZOOM',
 date_trunc('day', NOW()) + interval '14 hours',
 date_trunc('day', NOW()) + interval '15 hours',
 'Olivia Brooks',
 '["Olivia Brooks","Aarav Patel","Marcus Tan (Sales)","Northwind buying committee"]',
 'https://zoom.us/j/9988776655?pwd=demo',
 NULL, FALSE, 'CONFIRMED'),

-- Hidden / blocked by others — overlaps midday
('Busy', 'BLOCKED',
 date_trunc('day', NOW()) + interval '13 hours',
 date_trunc('day', NOW()) + interval '14 hours',
 'Unknown',
 '[]',
 NULL, NULL, TRUE, 'CONFIRMED'),

('Focus block — retry queue PR', 'PERSONAL',
 date_trunc('day', NOW()) + interval '16 hours',
 date_trunc('day', NOW()) + interval '17 hours',
 'Aarav Patel',
 '["Aarav Patel"]',
 NULL, NULL, FALSE, 'CONFIRMED'),

-- Tomorrow
('Sprint Planning — Q3 Cycle 1', 'TEAMS',
 date_trunc('day', NOW()) + interval '1 day 10 hours',
 date_trunc('day', NOW()) + interval '1 day 11 hours',
 'Dana White',
 '["Dana White","Aarav Patel","Kelly Morgan","Raj Patel","Mei Lin","Priya Shah"]',
 'https://teams.microsoft.com/l/meetup-bridge/sprint-plan',
 NULL, FALSE, 'CONFIRMED'),

('Design Review — Notifications v2', 'GOOGLE',
 date_trunc('day', NOW()) + interval '1 day 15 hours',
 date_trunc('day', NOW()) + interval '1 day 16 hours',
 'Mei Lin',
 '["Mei Lin","Aarav Patel","Kelly Morgan"]',
 'https://meet.google.com/des-rev-notify',
 NULL, FALSE, 'CONFIRMED'),

-- Day +2
('Lunch with Emily Watson', 'PERSONAL',
 date_trunc('day', NOW()) + interval '2 days 12 hours 30 minutes',
 date_trunc('day', NOW()) + interval '2 days 13 hours 30 minutes',
 'Emily Watson',
 '["Emily Watson","Aarav Patel"]',
 NULL, 'Cafe Magnolia, 5th & Main', FALSE, 'CONFIRMED'),

('Vendor sync — Datadog renewal', 'ZOOM',
 date_trunc('day', NOW()) + interval '2 days 16 hours',
 date_trunc('day', NOW()) + interval '2 days 16 hours 30 minutes',
 'Marcus Tan',
 '["Marcus Tan","Aarav Patel","Datadog Account Team"]',
 'https://zoom.us/j/5544332211',
 NULL, FALSE, 'CONFIRMED'),

-- Day +3
('All-hands — Q2 results + Q3 plan', 'TEAMS',
 date_trunc('day', NOW()) + interval '3 days 17 hours',
 date_trunc('day', NOW()) + interval '3 days 18 hours',
 'CEO Office',
 '["All ACME"]',
 'https://teams.microsoft.com/l/meetup-bridge/allhands',
 NULL, FALSE, 'CONFIRMED'),

-- Day +4
('Focus block — design doc: scheduler v2', 'PERSONAL',
 date_trunc('day', NOW()) + interval '4 days 14 hours',
 date_trunc('day', NOW()) + interval '4 days 16 hours',
 'Aarav Patel',
 '["Aarav Patel"]',
 NULL, NULL, FALSE, 'CONFIRMED'),

-- Day +6
('Family dinner — Patel household', 'PERSONAL',
 date_trunc('day', NOW()) + interval '6 days 19 hours',
 date_trunc('day', NOW()) + interval '6 days 21 hours',
 'Mom',
 '["Mom","Dad","Priya","Aarav Patel"]',
 NULL, 'Home', FALSE, 'CONFIRMED');

-- ---------------------------------------------------------------------------
-- 1 pending approval — Northwind integration scoping (Olivia Brooks)
-- proposed_start overlaps today's 16:00 Focus block
-- ---------------------------------------------------------------------------
INSERT INTO approvals (
    type, requester_name, requester_email, requested_time, status, created_at,
    message, proposal_title, proposed_start, duration_minutes, platform,
    conflict_event_title, conflict_event_start, conflict_event_end,
    alternatives, auto_reply_sent, decision_email_sent
) VALUES (
    'MEETING_PROPOSAL',
    'Olivia Brooks',
    'olivia.brooks@northwind-retail.com',
    date_trunc('day', NOW()) + interval '16 hours 30 minutes',
    'PENDING',
    NOW() - interval '25 minutes',
    'Hi! Following up on our demo — we''d love to scope the integration. Got 45 mins today?',
    'Northwind integration scoping',
    date_trunc('day', NOW()) + interval '16 hours 30 minutes',
    45,
    'ZOOM',
    'Focus block — retry queue PR',
    date_trunc('day', NOW()) + interval '16 hours',
    date_trunc('day', NOW()) + interval '17 hours',
    -- alternatives: 3 ISO timestamps (tomorrow 10:00, tomorrow 14:00, day+2 11:30)
    (jsonb_build_array(
       to_char(date_trunc('day', NOW()) + interval '1 day 10 hours', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
       to_char(date_trunc('day', NOW()) + interval '1 day 14 hours', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
       to_char(date_trunc('day', NOW()) + interval '2 days 11 hours 30 minutes', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
    )::text),
    -- auto_reply_sent: polite email offering alternatives
    'Hi Olivia,

Thanks so much for reaching out — I really enjoyed today''s demo and I''m keen to scope the integration.

Unfortunately 16:30 today overlaps a focus block I can''t move. Could any of these work instead?

  • Tomorrow (Wed) 10:00–10:45
  • Tomorrow (Wed) 14:00–14:45
  • Thursday 11:30–12:15

I''ll send a Zoom invite as soon as you confirm. Looking forward to it!

Best,
Aarav',
    NULL
);
