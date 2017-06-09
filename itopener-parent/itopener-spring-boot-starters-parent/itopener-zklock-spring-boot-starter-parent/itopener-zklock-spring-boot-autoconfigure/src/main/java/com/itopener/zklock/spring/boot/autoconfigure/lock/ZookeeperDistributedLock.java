package com.itopener.zklock.spring.boot.autoconfigure.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**  
 * @author fuwei.deng
 * @Date 2017年6月9日 下午3:10:58
 * @version 1.0.0
 */
public class ZookeeperDistributedLock extends AbstractDistributedLock {
	
	private final Logger logger = LoggerFactory.getLogger(ZookeeperDistributedLock.class);

	private CuratorFramework curatorFramework;
	
	private String lockPath;
	
	private Map<Long, InterProcessMutex> lockMap = new ConcurrentHashMap<Long, InterProcessMutex>();
	
	public ZookeeperDistributedLock(CuratorFramework curatorFramework, String lockPath) {
		this.curatorFramework = curatorFramework;
		this.lockPath = lockPath;
	}

	@Override
	public boolean lock(String key, long expire) {
		synchronized(key){
			long threadId = Thread.currentThread().getId();
			try {
				InterProcessMutex interProcessMutex = null;
				if(lockMap.containsKey(threadId)){
					interProcessMutex = lockMap.get(threadId);
				} else{
					interProcessMutex = new InterProcessMutex(curatorFramework, buildLockPath(key));
					lockMap.put(threadId, interProcessMutex);
				}
				boolean lock = interProcessMutex.acquire(expire, TimeUnit.SECONDS);
				if(lock){
					logger.info(threadId + " hold lock");
				}
				return lock;
			} catch (Exception e) {
				lockMap.remove(threadId);
				throw new RuntimeException(e);
			}
		}
	}
	
	private String buildLockPath(String key){
		return lockPath + "/" + key;
	}

	@Override
	public boolean releaseLock(String key) {
		long threadId = Thread.currentThread().getId();
		try {
			InterProcessMutex interProcessMutex = null;
			if(lockMap.containsKey(threadId)){
				interProcessMutex = lockMap.get(threadId);
				lockMap.remove(threadId);
			} else{
				interProcessMutex = new InterProcessMutex(curatorFramework, buildLockPath(key));
			}
			interProcessMutex.release();
			logger.info(threadId + " release lock");
		} catch (Exception e) {
//			throw new RuntimeException(e);
		} finally {
			lockMap.remove(threadId);
		}
		return true;
	}

}