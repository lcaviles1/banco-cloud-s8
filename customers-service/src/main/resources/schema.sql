CREATE TABLE IF NOT EXISTS customers(id varchar(40) primary key,name varchar(120) not null,email varchar(150) not null,status varchar(12) not null);
INSERT INTO customers VALUES('C001','Lucas Avilés','lucas@example.test','ACTIVE'),('C002','Cliente de prueba','cliente@example.test','ACTIVE') ON CONFLICT DO NOTHING;
