//
//This sample program is provided AS IS and may be used, executed, copied and
//modified without royalty payment by customer (a) for its own instruction and
//study, (b) in order to develop applications designed to run with an IBM
//WebSphere product, either for customer's own internal use or for redistribution
//by customer, as part of such an application, in customer's own products. "
//
//5724-J34 (C) COPYRIGHT International Business Machines Corp. 2009
//All Rights Reserved * Licensed Materials - Property of IBM
//
package com.devwebsphere.wxsutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import com.devwebsphere.wxsutils.WXSMapOfSets.Contains;
import com.devwebsphere.wxsutils.multijob.pingall.PingAllPartitionsJob;
import com.devwebsphere.wxsutils.wxsmap.BigListPushAgent;
import com.devwebsphere.wxsutils.wxsmap.dirtyset.FetchJobsFromAllDirtyListsJob;
import com.ibm.websphere.objectgrid.BackingMap;
import com.ibm.websphere.objectgrid.ObjectGrid;
import com.ibm.websphere.objectgrid.ObjectGridException;
import com.ibm.websphere.objectgrid.ObjectGridRuntimeException;

/**
 * This test connects to a grid running on the same box. Use the gettingstarted example
 * with the xml files in this folder. These xmls just add a third Map which doesn't
 * use client side caching.
 *
 */
public class TestClientAPIs 
{
	static ObjectGrid ogclient;
	static WXSUtils utils;
	static BackingMap bmFarMap3;
	
	@BeforeClass
	public static void setupTest()
		throws Exception
	{
		// do everything in one JVM for test
//		ogclient = WXSUtils.startTestServer("Grid", "/objectgrid.xml", "/deployment.xml");
		// switch to this to connect to remote grid instead.
		utils = WXSUtils.getDefaultUtils();
		ogclient = utils.getObjectGrid();
		bmFarMap3 = ogclient.getMap("FarMap3");
	}

	/**
	 * This clears the FarMap3 in preparation for any tests
	 */
	public static void clearMap()
	{
		try
		{
			ogclient.getSession().getMap("FarMap3").clear();
		}
		catch(ObjectGridException e)
		{
			Assert.fail("Exception during clear");
		}
	}
	
	@Test
	public void testPut()
	{
		clearMap();
		WXSMap<String, String> map = utils.getCache(bmFarMap3.getName());
		String value = "B";
		map.put("A", value);
		Assert.assertTrue(map.contains("A"));
		String v = map.get("A");
		Assert.assertEquals(value, v);
	}

	/**
	 * This tests the basic putAll/getAll/removeAll capabilities
	 */
	@Test
	public void testPutAll()
	{
		clearMap();
		for(int k = 0; k < 10; ++k)
		{
			int base = k * 1000;
			Map<String, String> batch = new HashMap<String, String>();
			for(int i = base; i < base + 1000; ++i)
			{
				batch.put("" + i, "V" + i);
			}
			utils.putAll(batch, bmFarMap3);
		}
		
		for(int k = 0; k < 10; ++k)
		{
			int base = k * 1000;
			ArrayList<String> keys = new ArrayList<String>();
			for(int i = base; i < base + 1000; ++i)
			{
				keys.add("" + i);
			}
			Map<String, String> rc = utils.getAll(keys, bmFarMap3);
			
			for(Map.Entry<String, String> e : rc.entrySet())
			{
				Assert.assertEquals("V" + e.getKey(), e.getValue());
			}

			utils.removeAll(keys, bmFarMap3);
			rc = utils.getAll(keys, bmFarMap3);
			
			for(Map.Entry<String, String> e : rc.entrySet())
			{
				Assert.assertNull(e.getValue());
			}
		}
	}
	
	@Test
	public void testCond_PutAll()
	{
		clearMap();
		Map<String, String> original = new HashMap<String, String>();
		for(int i = 0; i < 10; ++i)
		{
			original.put("" + i, "V" + i);
		}
		utils.putAll(original, bmFarMap3);
		
		Map<String, String> newValues = new HashMap<String, String>();
		for(int i = 0; i < 11; ++i)
		{
			newValues.put("" + i, "N" + i);
		}
		WXSMap<String, String> map = utils.getCache(bmFarMap3.getName());
		map.put("4", "DIFFERENT");

		// try with maps different size, orig = 10, new = 11
		try
		{
			Map<String, Boolean> rc = utils.cond_putAll(original, newValues, bmFarMap3);
			Assert.fail("Should have thrown exception");
		}
		catch(ObjectGridRuntimeException e)
		{
			// this is expected
		}
		// now make maps same size
		original.put("10", "DUMMY");
		Map<String, Boolean> rc = utils.cond_putAll(original, newValues, bmFarMap3);
		Assert.assertNotNull(rc);
		for(Map.Entry<String, Boolean> e : rc.entrySet())
		{
			Boolean b = rc.get(e.getKey());
			Assert.assertNotNull(b);
			if(e.getKey().equals("4"))
				Assert.assertFalse(b);
			else
				Assert.assertTrue(b);
		}
		for(int i = 0; i < 11; ++i)
		{
			String v = map.get("" + i);
			if(i != 4)
			{
				Assert.assertEquals("N" + i, v);
			}
			else
			{
				Assert.assertEquals("DIFFERENT", v);
			}
		}
	}
	
	@Test 
	public void testEmptyBulkOperations()
	{
		ArrayList<String> emptyList = new ArrayList<String>();
		utils.getAll(emptyList, bmFarMap3);
		utils.removeAll(emptyList, bmFarMap3);
		Map<String, String> emptyMap = new HashMap<String, String>();
		utils.putAll(emptyMap, bmFarMap3);
	}
	
	@Test
	public void testBigListOperations()
	{
		WXSMapOfLists<String, String> map = utils.getMapOfLists("BigList");
		Assert.assertNotNull(map);
		String key = "TEST";
		int numItems = BigListPushAgent.BUCKET_SIZE * 5;
		long startTime = System.nanoTime();

		// check popping an empty list returns the right value
		String rc = map.lpop(key);
		Assert.assertNull(rc);
		rc = map.rpop(key);
		Assert.assertNull(rc);
		
		for(int i = 0; i < numItems; ++i)
		{
			map.rpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}
		
		for(int i = numItems; i >= 0; --i)
		{
			map.rtrim(key, i);
			Assert.assertEquals(i, map.llen(key));
			if(i != 0)
			{
				ArrayList<String> list = map.lrange(key, i - 1, i - 1);
				Assert.assertNotNull(list);
				Assert.assertEquals(1, list.size());
				list = map.lrange(key, 0, i - 1);
				Assert.assertNotNull(list);
				Assert.assertEquals(i, list.size());
				for(int j = 0; j < i; ++j)
					Assert.assertEquals("" + j, list.get(j));
			}
		}

		for(int i = 0; i < numItems; ++i)
		{
			map.rpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}

		for(int j = 0; j < numItems; ++j)
		{
			ArrayList<String> result = map.lrange(key, j, numItems - 1);
			Assert.assertNotNull(result);
			Assert.assertEquals(numItems - j, result.size());
			for(int i = j; i < numItems; ++i)
			{
				Assert.assertEquals("" + i, result.get(i - j));
			}
		}
		
		for(int j = 0; j < numItems - 2; ++j)
		{
			ArrayList<String> result = map.lrange(key, j, j + 1);
			Assert.assertNotNull(result);
			Assert.assertEquals(2, result.size());
			for(int i = 0; i < 2; ++i)
			{
				Assert.assertEquals("" + (j + i), result.get(i));
			}
		}
		for(int i = 0; i < numItems; ++i)
		{
			String v = map.lpop(key);
			Assert.assertEquals("" + i, v);
			Assert.assertEquals(numItems - (i + 1), map.llen(key));
		}
		
		for(int i = 0; i < numItems; ++i)
		{
			map.lpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}
		
		for(int i = 0; i < numItems; ++i)
		{
			String v = map.rpop(key);
			Assert.assertEquals("" + i, v);
			Assert.assertEquals(numItems - (i + 1), map.llen(key));
		}
		
		for(int i = 0; i < numItems; ++i)
		{
			map.rpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}
		
		map.remove(key);
		Assert.assertEquals(0, map.llen(key));
		
		Assert.assertTrue(map.popAll(key).isEmpty());
		
		long endTime = System.nanoTime();
		double duration = (endTime - startTime) / 1000000.0;
		System.out.println("Took " + duration);
	}
	
	@Test
	public void testListOperations()
	{
		WXSMapOfLists<String, String> map = utils.getMapOfLists("BigList");
		Assert.assertNotNull(map);
		String key = "TEST";
		map.remove(key);
		int numItems = 10;
		for(int i = 0; i < numItems; ++i)
		{
			map.rpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}
		map.rtrim(key, numItems - 2);
		Assert.assertEquals(numItems - 2, map.llen(key));
		String item2 = map.rpop(key);
		Assert.assertEquals("" + (numItems - 3), item2);
		map.rpush(key, "" + (numItems - 3));
		map.rpush(key, "" + (numItems - 2));
		map.rpush(key, "" + (numItems - 1));
		Assert.assertEquals(numItems, map.llen(key));
		
		ArrayList<String> subList = map.lrange(key, 2, 4);
		Assert.assertEquals(3, subList.size());
		for(int i = 0; i < 3; ++i)
		{
			Assert.assertEquals("" + (i + 2), subList.get(i));
		}
		for(int i = 0; i < numItems; ++i)
		{
			String v = map.lpop(key);
			Assert.assertEquals("" + i, v);
			Assert.assertEquals(numItems - (i + 1), map.llen(key));
		}
		map.remove(key);
		Assert.assertEquals(0, map.llen(key));
		
		for(int i = 0; i < numItems; ++i)
		{
			map.lpush(key, "" + i);
			Assert.assertEquals(i + 1, map.llen(key));
		}
		for(int i = 0; i < numItems; ++i)
		{
			String v = map.rpop(key);
			Assert.assertEquals("" + i, v);
			Assert.assertEquals(numItems - (i + 1), map.llen(key));
		}
	}
	
	@Test
	public void testSetOperations()
	{
		WXSMapOfSets<String, String> map = utils.getMapOfSets("Set");
		Assert.assertNotNull(map);
		String key = "TEST";
		
		Assert.assertNull(map.get(key));
		
		int numKeys = 10;
		for(int i = 0; i < numKeys; ++i)
		{
			map.add(key, "" + i);
			Assert.assertEquals(i + 1, map.size(key));
			Assert.assertTrue(map.contains(key, Contains.ALL, "" + i));
		}
		map.add(key, "" + 0);
		Assert.assertEquals(numKeys, map.size(key));
		map.remove(key, "" + 0);
		Assert.assertFalse(map.contains(key, Contains.ALL, "" + 0));
		Assert.assertEquals(numKeys - 1, map.size(key));
		Set<String> values = map.get(key);
		Assert.assertNotNull(values);
		for(int i = 1; i < numKeys; ++i)
		{
			Assert.assertTrue(values.contains("" + i));
		}
		map.remove(key);
		Assert.assertNull(map.get(key));
		Assert.assertEquals(0, map.size(key));
		
		
		int maxSize = 3;
		for(int i = 0; i <= maxSize; ++i)
		{
			Set<String> ss = map.addAndFlush(key, maxSize, Integer.toString(i));
			Assert.assertNotNull(ss);
			Assert.assertEquals(0, ss.size());
		}
		Set<String> ss = map.addAndFlush(key, maxSize, "4");
		Assert.assertNotNull(ss);
		Assert.assertEquals(maxSize + 1, ss.size());
		Assert.assertEquals(1, map.size(key));
	}
	
	public boolean isDirty(String mapName, String dirtyKey, String listKey)
	{
		Set<String> set = FetchJobsFromAllDirtyListsJob.getAllDirtyKeysInGrid(ogclient, mapName, dirtyKey);
		return(set.contains(listKey));
	}
	
	@Test
	public void testDirtySet()
	{
		String dirtyKey = "DIRTY";
		WXSMapOfSets<String, String> dirtySetMap = utils.getMapOfSets("BigList_dirty");
		WXSMapOfLists<String, String> listMap = utils.getMapOfLists("BigList");
		
		// check set is empty
		Assert.assertEquals(0, dirtySetMap.size(dirtyKey));

		// check key L isn't in dirtySet
		Assert.assertFalse(isDirty("BigList", dirtyKey, "L"));

		// check with no dirty set key specified, the dirtySet doesn't change
		listMap.lpush("L", "HELLO");
		Assert.assertFalse(isDirty("BigList", dirtyKey, "L"));
		String rc = listMap.lpop("L");
		Assert.assertEquals("HELLO", rc);

		// now check it changes
		listMap.lpush("L", "HELLO", dirtyKey);
		
		// is L in the dirtySet?
		Assert.assertTrue(isDirty("BigList", dirtyKey, "L"));
		listMap.lpush("L", "BYE", dirtyKey);
		
		// check it's still there
		Assert.assertTrue(isDirty("BigList", dirtyKey, "L"));

		// one pop should leave it in there
		Assert.assertEquals("BYE", listMap.lpop("L", dirtyKey));
		Assert.assertTrue(isDirty("BigList", dirtyKey, "L"));

		// another pop should empty the list and remove it
		Assert.assertEquals("HELLO", listMap.lpop("L", dirtyKey));
		Assert.assertFalse(isDirty("BigList", dirtyKey, "L"));
		
		listMap.lpush("L", "0", dirtyKey);
		Assert.assertTrue(isDirty("BigList", dirtyKey, "L"));
		listMap.lpush("L", "1", dirtyKey);
		Assert.assertTrue(isDirty("BigList", dirtyKey, "L"));
		
		Assert.assertEquals(2, listMap.llen("L"));
		
		ArrayList<String> allValues = listMap.popAll("L", dirtyKey);
		Assert.assertFalse(isDirty("BigList", dirtyKey, "L")); // not dirty any more
		Assert.assertEquals(2, allValues.size());
		Assert.assertTrue(allValues.contains("0"));
		Assert.assertTrue(allValues.contains("1"));
	}
	
	@Test
    public void testDirtySetSize()
    {
            String dirtyKey = "DIRTY2";
            WXSMapOfLists<String, String> listMap = utils.getMapOfLists("BigList");
           
            Set<String> set = FetchJobsFromAllDirtyListsJob.getAllDirtyKeysInGrid(ogclient, "BigList", dirtyKey);
            Assert.assertEquals(0, set.size());
            
            int numKeys = 10;
            Set<String> keys = new HashSet<String>();
            for(int i = 0; i < numKeys; i++)
            {
                    String key = UUID.randomUUID().toString();
                    keys.add(key);
                    listMap.lpush(key, "HELLO"+i,dirtyKey);
                   
            }

            set = FetchJobsFromAllDirtyListsJob.getAllDirtyKeysInGrid(ogclient, "BigList", dirtyKey);
            Assert.assertEquals(keys.size(), set.size());

            int jobCounter = numKeys;
            while(true){
                    set = FetchJobsFromAllDirtyListsJob.getAllDirtyKeysInGrid(ogclient, "BigList", dirtyKey);
                   
                    if(set != null){
                            if(set.size() == 0)
                                    break;
                    }
                   
                    for(String key : set){
                            String value = listMap.lpop(key,dirtyKey);
                            if(value != null)
                            	--jobCounter;
                    }
                   
                    try {
                            Thread.sleep(5000);
                    } catch (InterruptedException e) {
                            e.printStackTrace();
                    }
            }
            Assert.assertEquals(0, jobCounter);
    }
	
	@Test
	public void testPingAllPartitions()
	{
		int partitionCount = PingAllPartitionsJob.visitAllPartitions(ogclient);
		Assert.assertEquals(ogclient.getMap("Set").getPartitionManager().getNumOfPartitions(), partitionCount);
	}
	/**
	 * This does a simple stress test against the grid.
	 */
//	@Test 
	public void testPutRate()
	{
		clearMap();
		int maxTests = 50;
		// run more than one time to allow JIT to settle
		// for unit test once is enough
		for(int loop = 0; loop < 1; ++loop)
		{
			for(int batchSize = 1000; batchSize <= 32000; batchSize *= 2 )
			{
				Map<String, String> batch = new HashMap<String, String>();
				for(int i = 0; i < batchSize; ++i)
					batch.put(Integer.toString(i), "V" + i);
				
				long start = System.nanoTime();
				for(int test = 0; test < maxTests; ++test)
				{
					utils.putAll(batch, ogclient.getMap("FarMap3"));
				}
				if(false)
				{
					ArrayList<String> keys = new ArrayList<String>();
					for(int i = 0; i < batchSize; ++i)
					{
						keys.add(Integer.toString(i));
					}
					Map<String, String> rc = utils.getAll(keys, ogclient.getMap("FarMap3"));
					
					for(Map.Entry<String, String> e : rc.entrySet())
					{
						Assert.assertEquals("V" + e.getKey(), e.getValue());
					}
				}
				double duration = (System.nanoTime() - start) / 1000000000.0;
				double rate = (double)batchSize * (double)maxTests / duration;
				System.out.println("Batch of " + batchSize + " rate is " + rate + " <" + (batch.size() * maxTests) + ":" + duration + ">");
			}
		}
	}
	
	@Test
	public void testMultiThread()
		throws InterruptedException
	{
		final String dirtyKey = "DIRTY3";
		final String key = "M";
        final WXSMapOfLists<String, String> listMap = utils.getMapOfLists("BigList");
        final int runTimeSeconds = 60 * 60; // 60 minutes
       
		Runnable pusher = new Runnable() {
			
			public void run() {
				try
				{
					long start = System.currentTimeMillis();
					long runTime = runTimeSeconds * 1000L;
					while(System.currentTimeMillis() < start + runTime)
					{
						listMap.lpush(key, UUID.randomUUID().toString(), dirtyKey);
						try
						{
//							Thread.currentThread().wait(10);
						}
						catch(Exception e) 
						{
							
						}
					}
				}
				catch(Exception e)
				{
					System.out.println("Pusher thread exception");
					e.printStackTrace(System.out);
				}
			}
		};
		
		Runnable puller = new Runnable() {
			public void run()
			{
				try
				{
					long start = System.currentTimeMillis();
					long runTime = runTimeSeconds * 1000L;
					while(System.currentTimeMillis() < start + runTime)
					{
						Set<String> allKeys = FetchJobsFromAllDirtyListsJob.getAllDirtyKeysInGrid(ogclient, "BigList", dirtyKey) ;
						for(String aKey : allKeys)
						{
							ArrayList<String> pList = listMap.popAll(aKey, dirtyKey);
							if(pList != null && pList.size() > 0)
								System.out.println("Pulled " + pList.size());
						}
						try
						{
							Thread.currentThread().wait(50);
						}
						catch(Exception e) 
						{}
						
					}
				}
				catch(Exception e)
				{
					System.out.println("Puller thread exception");
					e.printStackTrace(System.out);
				}
			}
		};

		ArrayList<Thread> allPushers = new ArrayList<Thread>();
		Thread pullerThread = new Thread(puller);
		pullerThread.start();
		for(int i = 0; i < 200; ++i)
		{
			Thread pusherThread = new Thread(pusher);
			allPushers.add(pusherThread);
			pusherThread.start();
			try
			{
				Thread.currentThread().sleep(10);
			}
			catch(InterruptedException e) {}
		}
		
		for(Thread t : allPushers)
			t.join();
		
		pullerThread.join();
	}
}
