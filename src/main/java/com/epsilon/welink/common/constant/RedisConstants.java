package com.epsilon.welink.common.constant;

// Redis 常量类，定义 Redis 相关的常量，如键前缀、过期时间等.
public class RedisConstants {

    public static final String USER_ONLINE_PREFIX = "user:online:";
    public static final String IM_ROUTE_PREFIX = "im:route:";
    public static final String USER_INFO_PREFIX = "user:info:";
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    public static final String REFRESH_TOKEN_PREFIX = "token:refresh:";
    public static final String FRIEND_APPLY_LOCK_PREFIX = "friend:apply:";
    public static final String GROUP_MEMBER_LOCK_PREFIX = "group:member:";
    public static final String IM_RATE_LIMIT_PREFIX = "im:rate:";
    public static final String IM_RECENT_PRIVATE_PREFIX = "im:recent:private:";
    public static final String IM_RECENT_GROUP_PREFIX = "im:recent:group:";
    public static final String IM_MESSAGE_DETAIL_PREFIX = "im:message:detail:";
    public static final String IM_GROUP_SEQ_PREFIX = "im:group:seq:";
    
    public static final long ONLINE_TTL_SECONDS = 120;
    public static final long ROUTE_TTL_SECONDS = 120;
    public static final long HEARTBEAT_TIMEOUT_SECONDS = 60;
    public static final long RECENT_MESSAGE_CACHE_DAYS = 7;
    public static final long RECENT_MESSAGE_CACHE_TTL_DAYS = 8;
}
