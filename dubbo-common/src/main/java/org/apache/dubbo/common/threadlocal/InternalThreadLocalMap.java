/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.dubbo.common.threadlocal;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The internal data structure that stores the threadLocal variables for Netty and all {@link InternalThread}s.
 * Note that this class is for internal use only. Use {@link InternalThread}
 * unless you know what you are doing.
 */
public final class InternalThreadLocalMap {

    private Object[] indexedVariables;

    private static ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<InternalThreadLocalMap>();

    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

    public static final Object UNSET = new Object();

    public static InternalThreadLocalMap getIfSet() {
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            return ((InternalThread) thread).threadLocalMap();
        }
        return slowThreadLocalMap.get();
    }

    public static InternalThreadLocalMap get() {
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            return fastGet((InternalThread) thread);
        }
        return slowGet();
    }

    public static void remove() {
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            ((InternalThread) thread).setThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    public static void destroy() {
        slowThreadLocalMap = null;
    }

    public static int nextVariableIndex() {
        int index = NEXT_INDEX.getAndIncrement();
        if (index < 0) {
            NEXT_INDEX.decrementAndGet();
            throw new IllegalStateException("Too many thread-local indexed variables");
        }
        return index;
    }

    public static int lastVariableIndex() {
        return NEXT_INDEX.get() - 1;
    }

    private InternalThreadLocalMap() {
        indexedVariables = newIndexedVariableTable();
    }

    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET;
    }

    /**
     * @return {@code true} if and only if a new thread-local variable has been created
     */
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        //数组容量还够，能放进去，那么可以直接设置
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
        } else {
            //扩容
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }

    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }

    public int size() {
        int count = 0;
        for (Object o : indexedVariables) {
            if (o != UNSET) {
                ++count;
            }
        }

        //the fist element in `indexedVariables` is a set to keep all the InternalThreadLocal to remove
        //look at method `addToVariablesToRemove`
        return count - 1;
    }

    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[32];
        Arrays.fill(array, UNSET);
        return array;
    }

    private static InternalThreadLocalMap fastGet(InternalThread thread) {
        //InternalThreadLocalMap 在 InternalThread 里面是一个变量维护的，可以直接通过 InternalThread.threadLocalMap() 获得：
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
        if (threadLocalMap == null) {
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        return threadLocalMap;
    }
    //原生 ThreadLocal 的 get 方法
    private static InternalThreadLocalMap slowGet() {
        ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = InternalThreadLocalMap.slowThreadLocalMap;
        InternalThreadLocalMap ret = slowThreadLocalMap.get();
        /** 对应调用到ThreadLocalMap.Entry e = map.getEntry(this);
         *  private Entry getEntry(ThreadLocal<?> key) {
         *     计算 hash 值，然后拿着 hash 值去数组里面取数据。如果取出来的数据不是我们想要的数据，则到getEntryAfterMiss
         *             int i = key.threadLocalHashCode & (table.length - 1);
         *             Entry e = table[i];
         *             if (e != null && e.get() == key)
         *                 return e;
         *             else
         *                 return getEntryAfterMiss(key, i, e);
         *         }
         * 》》》》 hash 冲突的解决方案 ：链式地址法（hashmap）； 再哈希法 ；建立公共溢出区；
         * 开放定址法(threadlocal使用开放定址法中的线性探测
         * 如果个位置的值已经存在了，那么就在原来的值上往后加一个单位，直至不发生哈希冲突，就像这样的：) ；
         *      private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
         *             Entry[] tab = table;
         *             int len = tab.length;
         *
         *             while (e != null) {
         *                 ThreadLocal<?> k = e.get();
         *                 if (k == key)
         *                     return e;
         *                 if (k == null)
         *                 w针对内存泄漏做的一些改进  ，这些代码还没看太明白
         *                     expungeStaleEntry(i);
         *                 else
         *                     i = nextIndex(i, len);
         *                 e = tab[i];
         *             }
         *             return null;
         *         }
         */
        if (ret == null) {
            ret = new InternalThreadLocalMap();
            slowThreadLocalMap.set(ret);
        }
        return ret;
    }

    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity = index;
        newCapacity |= newCapacity >>> 1;
        newCapacity |= newCapacity >>> 2;
        newCapacity |= newCapacity >>> 4;
        newCapacity |= newCapacity >>> 8;
        newCapacity |= newCapacity >>> 16;
        newCapacity++;
        //扩容就是变成原来大小的 2 倍,把原数组里面的值拷贝到新的数组里面去。
        //剩下的部分用 UNSET 填充。最后把我们传进来的 value 放到指定位置上。
        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;
        indexedVariables = newArray;
    }
}
