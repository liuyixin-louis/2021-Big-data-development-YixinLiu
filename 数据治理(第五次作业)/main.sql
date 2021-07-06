
-- create table
drop table if exists data_homework.MALL;
create table data_homework.MALL
(
  mall_id INT PRIMARY KEY,
  user_table INT,
  merchant_table   INT
);
comment on table data_homework.MALL
  is '商城';
comment on column data_homework.MALL.mall_id
  is '商城id';
comment on column data_homework.MALL.user_table
  is '用户表注册id';
comment on column data_homework.MALL.merchant_table
  is '商家表注册id';
drop table if exists data_homework.USER_REGISTER;
create table data_homework.USER_REGISTER
(
  register_id INT PRIMARY KEY,
  user_id INT,
  register_time  timestamp
);
comment on table data_homework.USER_REGISTER
  is '用户注册表';
comment on column data_homework.USER_REGISTER.register_id
  is '用户注册表id';
comment on column data_homework.USER_REGISTER.user_id
  is '用户id';
comment on column data_homework.USER_REGISTER.register_time
  is '注册时间';
drop table if exists data_homework.MERCHANT_REGISTER;
create table data_homework.MERCHANT_REGISTER
(
  register_id INT PRIMARY KEY,
  merchant_id INT,
  register_time  timestamp
);
comment on table data_homework.MERCHANT_REGISTER
  is '商家入驻表';
comment on column data_homework.MERCHANT_REGISTER.register_id
  is '商家入驻表id';
comment on column data_homework.MERCHANT_REGISTER.merchant_id
  is '商家id';
comment on column data_homework.MERCHANT_REGISTER.register_time
  is '入驻时间';
drop table if exists data_homework.USERS;
create table data_homework.USERS
(
  user_id INT PRIMARY KEY,
  gender VARCHAR(1),
  age  INT,
  phone_number VARCHAR(11),
  order_id INT
);
comment on table data_homework.USERS
  is '用户表';
comment on column data_homework.USERS.user_id
  is '用户id';
comment on column data_homework.USERS.gender
  is '性别';
comment on column data_homework.USERS.age
  is '年龄';
 comment on column data_homework.USERS.phone_number
  is '手机号';
  comment on column data_homework.USERS.order_id
  is '订单号';
drop table if exists data_homework.MERCHANT;
create table data_homework.MERCHANT
(
  merchant_id INT PRIMARY KEY,
  gender VARCHAR(1),
  age  INT,
  phone_number VARCHAR(11),
  goods_id INT
);
comment on table data_homework.MERCHANT
  is '商家表';
comment on column data_homework.MERCHANT.merchant_id
  is '商家id';
comment on column data_homework.MERCHANT.gender
  is '性别';
comment on column data_homework.MERCHANT.age
  is '年龄';
 comment on column data_homework.MERCHANT.phone_number
  is '手机号';
  comment on column data_homework.MERCHANT.goods_id
  is '货物号';
drop table if exists data_homework.ORDERS;
create table data_homework.ORDERS
(
  order_id INT PRIMARY KEY,
  place VARCHAR(30),
  city VARCHAR(30),
  time  timestamp
);
comment on table data_homework.ORDERS
  is '订单表';
comment on column data_homework.ORDERS.order_id
  is '订单号';
comment on column data_homework.ORDERS.place
  is '收货地点';
comment on column data_homework.ORDERS.city
  is '收货城市';
 comment on column data_homework.ORDERS.time
  is '下单时间';
drop table if exists data_homework.SHOP;
create table data_homework.SHOP
(
  goods_id INT PRIMARY KEY,
  time  timestamp
);
comment on table data_homework.SHOP
  is '上架表';
comment on column data_homework.SHOP.goods_id
  is '商品id';
 comment on column data_homework.SHOP.time
  is '上架时间';
drop table if exists data_homework.GOODS;
create table data_homework.GOODS
(
  goods_id INT PRIMARY KEY,
  good_type VARCHAR(30)
);
comment on table data_homework.GOODS
  is '商品表';
comment on column data_homework.GOODS.goods_id
  is '商品id';
 comment on column data_homework.GOODS.good_type
  is '商品类型';

  
-- query 
-- 月查询总体销售额
select A.month,count(A.month) from (
	select time,EXTRACT(MONTH FROM time)as month from data_homework.orders group by time
)as A
group by A.month

-- 月查询新增注册人数
select A.month,count(A.month) as person_num from (
	select register_time,EXTRACT(MONTH FROM register_time)as month from data_homework.user_register group by register_time
)as A
group by A.month

--月查看总体的销售额
select A.month,count(A.month) as person_num from (
	select register_time,EXTRACT(MONTH FROM register_time)as month from data_homework.user_register group by register_time
)as A
group by A.month

--月查看城市的销售额
select A.city,A.month ,count(A.month)
from (select time,city,EXTRACT(MONTH FROM time)as month from data_homework.orders group by time,city) as A
group by A.city,A.month

--月查看城市、商品的类别销售额
select D.city,D.good_type,count(D.month)
from
	(select  C.city,EXTRACT(MONTH FROM C.time)as month,C.good_type
	from 
		(SELECT  A.city, A."time", B.good_type 
		FROM data_homework.orders as A INNER JOIN data_homework.goods as B
		ON A.order_id=B.goods_id) as C
	group by C.city,C.time,C.good_type) as D
group by D.city,D.good_type

--月查看性别、商品类型的销售额
select E.gender,E.good_type,count(E.month)
from 
	(select D.gender,EXTRACT(MONTH FROM D.time)as month,D.good_type
	from
		(SELECT  C.gender , A."time",B.good_type 
		FROM data_homework.orders as A 
			INNER JOIN data_homework.goods as B 
			ON A.order_id=B.goods_id 
			INNER JOIN data_homework.users as C
			ON A.order_id =C.order_id ) as D
	group by D.gender,D.time,D.good_type ) as E
group by E.gender,E.good_type