CREATE TABLE IF NOT EXISTS accounts(id bigserial primary key,owner varchar(40) not null,type varchar(12) not null,balance numeric(16,2) not null check(balance>=0),status varchar(8) not null);
CREATE TABLE IF NOT EXISTS ledger(payment_id varchar(36) primary key,fingerprint text not null,applied boolean not null);
INSERT INTO accounts(id,owner,type,balance,status) VALUES(101,'C001','SAVINGS',5000,'OPEN'),(102,'C002','SAVINGS',8000,'OPEN') ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('accounts','id'),200) FROM accounts_id_seq WHERE last_value<200;
