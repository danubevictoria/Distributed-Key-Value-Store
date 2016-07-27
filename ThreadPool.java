package edu.berkeley.cs162;
/**
 * Java Thread Pool
 *
 * @author Prashanth Mohan (http://www.cs.berkeley.edu/~prmohan)
 *
 * Copyright (c) 2011, University of California at Berkeley
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of University of California, Berkeley nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL PRASHANTH MOHAN BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
//package edu.berkeley.cs162;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



public class ThreadPool {
       /**
        * Set of threads in the threadpool
        */
       protected Thread threads[] = null;

       protected ArrayList<Runnable> taskQueue;


       protected Lock lock = new ReentrantLock();
       private Lock queueAccessLock = new ReentrantLock();

       /**
        * Initialize the number of threads required in the threadpool.
        *
        * @param size  How many threads in the thread pool.
        */
       public ThreadPool(int size)
       {
               threads = new Thread[size];
               taskQueue = new ArrayList<Runnable>();
               for (Thread t: threads){
                       t = new WorkerThread(this);
                       t.start(); //runs workerThread.
               }
       }

       public Lock getLock(){
               return queueAccessLock;
       }

       /**
        * Add a job to the queue of tasks that has to be executed. As soon
as a thread is available,
        * it will retrieve tasks from this queue and start processing.
        * @param r job that has to be executed asynchronously
        * @throws InterruptedException
        */
       public void addToQueue(Runnable r) throws InterruptedException
       {
               lock.lock();
               try{
               taskQueue.add(r);
               }
               finally{
                       lock.unlock();
               }
       }
}

/**
 * The worker threads that make up the thread pool.
 */
class WorkerThread extends Thread {
       /**
        * @param o the thread pool
        */

       ThreadPool myPool;
       Lock myAccessLock;
       Runnable r;
       Boolean taskAcquired;

       WorkerThread(ThreadPool o)
       {
               myPool = o;
               myAccessLock = myPool.getLock();
               taskAcquired = false;
       }

       /**
        * Scan and execute tasks.
        */
       public void run()
       {
               while(true){
                       myAccessLock.lock();
                       if (myPool.taskQueue.size() != 0){
                               r = myPool.taskQueue.remove(0);
                               taskAcquired = true;
                       }
                       myAccessLock.unlock();
                       if (taskAcquired){
                               r.run();
                               taskAcquired = false;
                       }
               }
       }
}