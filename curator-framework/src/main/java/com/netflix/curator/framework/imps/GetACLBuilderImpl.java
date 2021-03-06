/*
 *
 *  Copyright 2011 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.curator.framework.imps;

import com.netflix.curator.RetryLoop;
import com.netflix.curator.TimeTrace;
import com.netflix.curator.framework.api.BackgroundCallback;
import com.netflix.curator.framework.api.CuratorEventType;
import com.netflix.curator.framework.api.GetACLBuilder;
import com.netflix.curator.framework.api.Pathable;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

class GetACLBuilderImpl implements GetACLBuilder, BackgroundOperation<String>
{
    private final CuratorFrameworkImpl client;

    private Backgrounding               backgrounding;
    private Stat                        responseStat;

    GetACLBuilderImpl(CuratorFrameworkImpl client)
    {
        this.client = client;
        backgrounding = new Backgrounding();
        responseStat = null;
    }

    @Override
    public Pathable<List<ACL>> inBackground()
    {
        backgrounding = new Backgrounding(true);
        return this;
    }

    @Override
    public Pathable<List<ACL>> inBackground(Object context)
    {
        backgrounding = new Backgrounding(context);
        return this;
    }

    @Override
    public Pathable<List<ACL>> inBackground(BackgroundCallback callback)
    {
        backgrounding = new Backgrounding(callback);
        return this;
    }

    @Override
    public Pathable<List<ACL>> inBackground(BackgroundCallback callback, Executor executor)
    {
        backgrounding = new Backgrounding(client, callback, executor);
        return this;
    }

    @Override
    public Pathable<List<ACL>> storingStatIn(Stat stat)
    {
        responseStat = stat;
        return this;
    }

    @Override
    public void performBackgroundOperation(final OperationAndData<String> operationAndData) throws Exception
    {
        final TimeTrace             trace = client.getZookeeperClient().startTracer("GetACLBuilderImpl-Background");
        AsyncCallback.ACLCallback   callback = new AsyncCallback.ACLCallback()
        {
            @Override
            public void processResult(int rc, String path, Object ctx, List<ACL> acl, Stat stat)
            {
                trace.commit();
                CuratorEventImpl event = new CuratorEventImpl(client, CuratorEventType.GET_ACL, rc, path, null, ctx, stat, null, null, null, acl);
                client.processBackgroundOperation(operationAndData, event);
            }
        };
        client.getZooKeeper().getACL(operationAndData.getData(), responseStat, callback, backgrounding.getContext());
    }

    @Override
    public List<ACL> forPath(String path) throws Exception
    {
        path = client.fixForNamespace(path);

        List<ACL>       result = null;
        if ( backgrounding.inBackground() )
        {
            client.processBackgroundOperation(new OperationAndData<String>(this, path, backgrounding.getCallback(), null), null);
        }
        else
        {
            result = pathInForeground(path);
        }
        return result;
    }

    private List<ACL> pathInForeground(final String path) throws Exception
    {
        TimeTrace    trace = client.getZookeeperClient().startTracer("GetACLBuilderImpl-Foreground");
        List<ACL>    result = RetryLoop.callWithRetry
        (
            client.getZookeeperClient(),
            new Callable<List<ACL>>()
            {
                @Override
                public List<ACL> call() throws Exception
                {
                    return client.getZooKeeper().getACL(path, responseStat);
                }
            }
        );
        trace.commit();
        return result;
    }
}
