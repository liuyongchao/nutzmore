package org.nutz.integration.jedisque.pubsub;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nutz.integration.jedisque.JedisqueAgent;
import org.nutz.lang.Streams;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import redis.clients.jedis.Jedis;

public class PubSubService {
    
    private static final Log log = Logs.get();
    
    protected JedisqueAgent jedisAgent;
    
    protected List<PubSubProxy> list = new ArrayList<PubSubProxy>();
    protected Set<String> patterns = new HashSet<String>();

    public void reg(final String pattern, PubSub pb) {
        final PubSubProxy proxy = new PubSubProxy(pattern, pb);
        list.add(proxy);
        Thread t = new Thread("jedisque.pubsub." + pattern) {
            public void run() {
                while (patterns.contains(pattern)) {
                    try {
                        jedisAgent.jedis().psubscribe(proxy, pattern);
                    } catch (Exception e) {
                        if (!patterns.contains(pattern))
                            break;
                        log.warn("something wrong!! sleep 3s", e);
                        try {
                            Thread.sleep(3000);
                        }
                        catch (Throwable _e) {
                            break;
                        }
                    }
                }
            }
        };
        t.start();
        patterns.add(pattern);
    }
    
    public void fire(String channel, String message) {
        log.debugf("publish channel=%s msg=%s", channel, message);
        Jedis jedis = null;
        try {
            jedis = jedisAgent.jedis();
            jedis.publish(channel, message);
        } finally {
            Streams.safeClose(jedis);
        }
    }

    public void depose() {
        for (PubSubProxy proxy : list)
            try {
                patterns.remove(proxy.pattern);
                proxy.punsubscribe(proxy.pattern);
            }
            catch (Exception e) {
                log.debug("punsubscribe " + proxy.pattern, e);
            }
    }
    
    public void setJedisAgent(JedisqueAgent jedisAgent) {
        this.jedisAgent = jedisAgent;
    }
}
