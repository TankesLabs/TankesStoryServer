package database;

import config.YamlConfig;
import redis.clients.jedis.Jedis;

public class RedisManager {

    private static Jedis jedis;

    public static void connect() {
        jedis = new Jedis(YamlConfig.config.server.REDIS_HOST, YamlConfig.config.server.REDIS_PORT);
    }

    public static Jedis getClient() {
        return jedis;
    }

    public static void close() {
        jedis.close();
    }
}