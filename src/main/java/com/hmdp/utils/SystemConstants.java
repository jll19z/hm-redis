package com.hmdp.utils;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = "D:\\Environment\\nginx\\nginx-1.18.0\\html\\hmdp\\imgs\\";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;


    //redis
    public static final String LOGIN_CODE_KEY ="login:code:";
    public static final Long LOGIN_CODE_TTL =2L;

    public static final String LOGIN_USER_KEY ="login:token:";
    public static final Long LOGIN_USER_TTL =30L;

    public static final String CACHE_SHOP_KEY= "cache:shop:";


    public static final Long CACHE_COMMON_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 5L;


    public static final String LOCK_SHOP_KEY= "lock:shop:";
    public static final Long LOCK_COMMON_TTL = 10L;


    public static final String SECKILL_STOCK_KEY= "seckill:stock:";
}
