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
import java.util.Map;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.websphere.objectgrid.ObjectGrid;
import com.ibm.websphere.objectgrid.ObjectGridException;

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
	
	@BeforeClass
	public static void setupTest()
	{
//		ogclient = WXSUtils.startTestServer("Grid", "/objectgrid.xml", "/deployment.xml");
		ogclient = WXSUtils.connectClient("localhost:2809", "Grid", "/objectgrid.xml");
		utils = new WXSUtils(ogclient);
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
	public void testPutAll()
	{
		for(int k = 0; k < 10; ++k)
		{
			int base = k * 1000;
			Map<String, String> batch = new HashMap<String, String>();
			for(int i = base; i < base + 1000; ++i)
			{
				batch.put("" + i, "V" + i);
			}
			utils.putAll(batch, ogclient.getMap("FarMap3"));
		}
		
		for(int k = 0; k < 10; ++k)
		{
			int base = k * 1000;
			ArrayList<String> keys = new ArrayList<String>();
			for(int i = base; i < base + 1000; ++i)
			{
				keys.add("" + i);
			}
			Map<String, String> rc = utils.getAll(keys, ogclient.getMap("FarMap3"));
			
			for(Map.Entry<String, String> e : rc.entrySet())
			{
				Assert.assertEquals("V" + e.getKey(), e.getValue());
			}

			utils.removeAll(keys, ogclient.getMap("FarMap3"));
			rc = utils.getAll(keys, ogclient.getMap("FarMap3"));
			
			for(Map.Entry<String, String> e : rc.entrySet())
			{
				Assert.assertNull(e.getValue());
			}
		}
	}
	
	@Test 
	public void testPutRate()
	{
		int maxTests = 50;
		// run five times to allow JIT to settle
		for(int loop = 0; loop < 5; ++loop)
		{
			for(int batchSize = 1000; batchSize <= 32000; batchSize *= 2 )
			{
				Map<String, String> batch = new HashMap<String, String>();
				for(int i = 0; i < batchSize; ++i)
					batch.put(Integer.toString(i), "K" + i);
				
				long start = System.nanoTime();
				for(int test = 0; test < maxTests; ++test)
				{
					utils.putAll(batch, ogclient.getMap("FarMap3"));
				}
				double duration = (System.nanoTime() - start) / 1000000000.0;
				double rate = (double)batchSize * (double)maxTests / duration;
				System.out.println("Batch of " + batchSize + " rate is " + rate + " <" + (batch.size() * maxTests) + ":" + duration + ">");
			}
		}
	}
}
