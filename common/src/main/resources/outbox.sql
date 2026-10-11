CREATE TABLE IF NOT EXISTS outbox(id varchar(36) primary key,target varchar(8) not null,topic varchar(100) not null,event_key varchar(100) not null,payload text not null,sent boolean not null default false,attempts int not null default 0,created timestamp not null default current_timestamp);
CREATE INDEX IF NOT EXISTS outbox_pending ON outbox(sent,created);
CREATE TABLE IF NOT EXISTS inbox(event_id varchar(36) primary key,channel varchar(20) not null,payload text not null,received timestamp not null default current_timestamp);
