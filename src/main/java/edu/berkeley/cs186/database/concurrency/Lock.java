package edu.berkeley.cs186.database.concurrency;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Each table will have a lock object associated with it in order
 * to implement table-level locking. The lock will keep track of its
 * transaction owners, type, and the waiting queue.
 */
public class Lock {


  private Set<Long> transactionOwners;
  private ConcurrentLinkedQueue<LockRequest> transactionQueue;
  private LockManager.LockType type;

  public Lock(LockManager.LockType type) {
    this.transactionOwners = new HashSet<Long>();
    this.transactionQueue = new ConcurrentLinkedQueue<LockRequest>();
    this.type = type;
  }

  protected Set<Long> getOwners() {
    return this.transactionOwners;
  }

  public LockManager.LockType getType() {
    return this.type;
  }

  private void setType(LockManager.LockType newType) {
    this.type = newType;
  }

  public int getSize() {
    return this.transactionOwners.size();
  }

  public boolean isEmpty() {
    return this.transactionOwners.isEmpty();
  }

  private boolean containsTransaction(long transNum) {
    return this.transactionOwners.contains(transNum);
  }

  private void addToQueue(long transNum, LockManager.LockType lockType) {
    LockRequest lockRequest = new LockRequest(transNum, lockType);
    this.transactionQueue.add(lockRequest);
  }

  private void removeFromQueue(long transNum, LockManager.LockType lockType) {
    LockRequest lockRequest = new LockRequest(transNum, lockType);
    this.transactionQueue.remove(lockRequest);
  }

  private void addOwner(long transNum) {
    this.transactionOwners.add(transNum);
  }

  private void removeOwner(long transNum) {
    this.transactionOwners.remove(transNum);
  }

  /**
   * Attempts to resolve the specified lockRequest. Adds the request to the queue
   * and calls wait() until the request can be promoted and removed from the queue.
   * It then modifies this lock's owners/type as necessary.
   * @param transNum transNum of the lock request
   * @param lockType lockType of the lock request
   */
  protected synchronized void acquire(long transNum, LockManager.LockType lockType) {
    //if the transaction is already in the lock request queue, simply block the lock request, dont add it to the queue.
     if (this.transactionQueue.size() > 0) {
       for (LockRequest lr : this.transactionQueue) {
         if (transNum == lr.transNum) {
           return;
         }
       }
     }
    if (this.getType().equals(LockManager.LockType.EXCLUSIVE) && this.containsTransaction(transNum)) {
      return;
    }
    if (!this.isCompatible(transNum, lockType)) {
      //add to the queue and call 'wait()'
      this.addToQueue(transNum, lockType);
      //loop testing condition and calling wait()
      while  (!this.isCompatible(transNum, lockType)) {
        try {
          wait();
        } catch (InterruptedException e) {
//          e.printStackTrace();
        }
      }
      this.removeFromQueue(transNum, lockType);
    }
    //obtain the lock.
    this.addOwner(transNum); //add owner
    this.setType(lockType);
    return;
  }

/*
if it is compatible with the transactions that own this lock object and the type of the lock object
(based on the compatibility matrix between shared and exclusive locks defined in lecture)
and ONE of the following conditions is true:
  the request is an upgrade (from shared to exclusive)
  the request is in front of the queue
  the request is for a shared lock and there are only other shared locks in front of it on the queue - request_s_and_s
*/
  public Boolean isCompatible(long transNum, LockManager.LockType lockType) {
    //check compatibility matrix, if not compatible, return false, else continued to check for conditions
    if (this.isEmpty()) {
      if (lockType.equals(LockManager.LockType.SHARED)) {
        return true;
      }
      else {
        if (this.transactionQueue.isEmpty() || this.transactionQueue.peek().transNum == transNum) {
           return true;
        }
      }
    }
    if (!(this.getType().equals(LockManager.LockType.SHARED) && (lockType.equals(LockManager.LockType.SHARED) || (this.containsTransaction(transNum) && this.getSize() <= 1)))){
      return false;
    }
    ////////////////////////
    Boolean request_is_upgrade = false;
    if (this.containsTransaction(transNum) && this.getSize() == 1 && lockType.equals(LockManager.LockType.EXCLUSIVE) && this.getType().equals(LockManager.LockType.SHARED)) {
      request_is_upgrade = true;
    }
    ////////////////
    Boolean request_at_front = false;
    if (this.transactionQueue.size() > 0) {
      LockRequest first = this.transactionQueue.peek();
      if (this.getType().equals(LockManager.LockType.SHARED) && lockType.equals(LockManager.LockType.SHARED) && first.transNum == transNum && first.lockType.equals(lockType)) {
        request_at_front = true;
      }
    } else {
      request_at_front = true;
    }
    //////////////////
    Boolean request_s_and_s = true;
    if (this.getType().equals(LockManager.LockType.SHARED) && lockType.equals(LockManager.LockType.SHARED)) {
      if (!this.transactionQueue.isEmpty()) {
        for (LockRequest lr : this.transactionQueue) {
          if (lr.transNum == transNum) {
            break;
          }
          if (lr.lockType.equals(LockManager.LockType.EXCLUSIVE)) {
            request_s_and_s = false;
            break;
          }
        }
      }
    } else {
      request_s_and_s = false;
    }
    /////////////////////
//    Boolean other_transaction_requesting_upgrade = false;
//    for (LockRequest lr : this.transactionQueue) {
//      if (this.containsTransaction(lr.transNum) && lr.lockType.equals(LockManager.LockType.EXCLUSIVE) && this.getType().equals(LockManager.LockType.SHARED)){
//        other_transaction_requesting_upgrade = true;
//      }
//    }
    /////////////

    return (request_at_front || request_is_upgrade || request_s_and_s); //&& !other_transaction_requesting_upgrade);
  }

  /**
   * transNum releases ownership of this lock
   * @param transNum transNum of transaction that is releasing ownership of this lock
   */
  protected synchronized void release(long transNum) {
    if (this.containsTransaction(transNum)){
      this.removeOwner(transNum);
      if (this.getType().equals(LockManager.LockType.EXCLUSIVE)) {
        this.setType(LockManager.LockType.SHARED);
      }
      notifyAll();
    }

    return;
  }

  /**
   * Checks if the specified transNum holds a lock of lockType on this lock object
   * @param transNum transNum of lock request
   * @param lockType lock type of lock request
   * @return true if transNum holds the lock of type lockType
   */
  protected synchronized boolean holds(long transNum, LockManager.LockType lockType) {
    return this.containsTransaction(transNum) && this.getType().equals(lockType);
  }

  /**
   * LockRequest objects keeps track of the transNum and lockType.
   * Two LockRequests are equal if they have the same transNum and lockType.
   */
  private class LockRequest {
      private long transNum;
      private LockManager.LockType lockType;
      private LockRequest(long transNum, LockManager.LockType lockType) {
        this.transNum = transNum;
        this.lockType = lockType;
      }

      @Override
      public int hashCode() {
        return (int) transNum;
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof LockRequest))
          return false;
        if (obj == this)
          return true;

        LockRequest rhs = (LockRequest) obj;
        return (this.transNum == rhs.transNum) && (this.lockType == rhs.lockType);
      }

  }

}
