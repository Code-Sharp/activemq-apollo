/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.queue;

import java.util.Collection;

import org.apache.activemq.broker.store.BrokerDatabase.OperationContext;
import org.apache.activemq.flow.ISourceController;
import org.apache.activemq.protobuf.AsciiBuffer;

public interface QueueStore<K, V> {

    public interface SaveableQueueElement<V> {
        /**
         * Gets the element to save.
         * 
         * @return
         */
        public V getElement();

        /**
         * Gets the sequence number of the element in the queue
         * 
         * @return
         */
        public long getSequenceNumber();

        /**
         * @return a return value of true will cause {@link #notifySave()} to
         *         called when this element is persisted
         */
        public boolean requestNotify();

        /**
         * Called when the element has been saved.
         * 
         * @return
         */
        public boolean notifySave();
    }

    /**
     * A holder for queue elements loaded from the store.
     * 
     */
    public interface RestoredElement<V> {
        /**
         * @return Gets the restored element (possibly null if not requested)
         * @throws Exception
         */
        public V getElement() throws Exception;

        /**
         * @return The element size.
         */
        int getElementSize();

        /**
         * Returns the sequence number of this element in the queue
         * 
         * @return the sequence number of this element
         */
        long getSequenceNumber();

        /**
         * Gets the tracking number of the stored message.
         * 
         * @return the next sequence number
         */
        long getStoreTracking();

        /**
         * Gets the next sequence number in the queue after this one or -1 if
         * this is the last stored element
         * 
         * @return the next sequence number
         */
        long getNextSequenceNumber();
    }

    /**
     * A callback to be used with {@link #elementsRestored(Collection)} to pass
     * the results of a call to
     * {@link QueueStore#restoreQueueElements(QueueDescriptor, long, long, int, RestoreListener)}
     */
    public interface RestoreListener<V> {

        public void elementsRestored(Collection<RestoredElement<V>> restored);
    }

    public static class QueueDescriptor {

        public static final short SHARED = 0;
        public static final short SHARED_PRIORITY = 1;
        public static final short PARTITIONED = 2;

        AsciiBuffer queueName;
        AsciiBuffer parent;
        int partitionKey;
        short applicationType;
        short queueType = SHARED;

        public QueueDescriptor() {
        }

        public QueueDescriptor(QueueDescriptor toCopy) {
            if (toCopy == null) {
                return;
            }
            queueName = toCopy.queueName;
            applicationType = toCopy.applicationType;
            queueType = toCopy.queueType;
            partitionKey = toCopy.partitionKey;
            parent = toCopy.parent;
        }

        public QueueDescriptor copy() {
            return new QueueDescriptor(this);
        }

        public int getPartitionKey() {
            return partitionKey;
        }

        public void setPartitionId(int key) {
            this.partitionKey = key;
        }

        /**
         * Sets the queue type which is useful for querying of queues. The value
         * must not be less than 0.
         * 
         * @param type
         *            The type of the queue.
         */
        public void setApplicationType(short type) {
            if (type < 0) {
                throw new IllegalArgumentException();
            }
            applicationType = type;
        }

        /**
         * @param type
         *            The type of the queue.
         */
        public short getApplicationType() {
            return applicationType;
        }

        public short getQueueType() {
            return queueType;
        }

        public void setQueueType(short type) {
            queueType = type;
        }

        /**
         * If this queue is a partition of a parent queue, this should be set to
         * the parent queue's name.
         * 
         * @return The parent queue's name
         */
        public AsciiBuffer getParent() {
            return parent;
        }

        /**
         * If this queue is a partition of a parent queue, this should be set to
         * the parent queue's name.
         */
        public void setParent(AsciiBuffer parent) {
            this.parent = parent;
        }

        public AsciiBuffer getQueueName() {
            return queueName;
        }

        public void setQueueName(AsciiBuffer queueName) {
            this.queueName = queueName;
        }

        public int hashCode() {
            return queueName.hashCode();
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }

            if (o instanceof QueueDescriptor) {
                return equals((QueueDescriptor) o);
            } else {
                return false;
            }
        }

        public boolean equals(QueueDescriptor qd) {
            if (qd.queueName.equals(queueName)) {
                return true;
            }
            return false;
        }
    }

    /**
     * Loads a series of elements for the specified queue. The loaded messages
     * are given to the provided {@link MessageRestoreListener}.
     * <p>
     * <b><i>NOTE:</i></b> This method uses the queue sequence number for the
     * message not the store tracking number.
     * 
     * @param queue
     *            The queue for which to load messages
     * @param recordOnly
     *            True if only the record data should be returned (excluding the
     *            element itself)
     * @param firstSequence
     *            The first queue sequence number to load (-1 starts at
     *            beginning)
     * @param maxSequence
     *            The maximum sequence number to load (-1 if no limit)
     * @param maxCount
     *            The maximum number of messages to load (-1 if no limit)
     * @param listener
     *            The listener to which restored elements should be passed.
     * @return The {@link OperationContext} associated with the operation
     */
    public void restoreQueueElements(QueueDescriptor queue, boolean recordOnly, long firstSequence, long maxSequence, int maxCount, RestoreListener<V> listener);

    /**
     * Asynchronously deletes an element from the store.
     * 
     * @param descriptor
     *            The queue descriptor
     * @param element
     *            The element to delete.
     */
    public void deleteQueueElement(QueueDescriptor descriptor, V element);

    /**
     * Asynchronously saves the given element to the store
     * 
     * @param descriptor
     *            The descriptor for the queue.
     * @param controller
     *            A flow controller to use in the event that there isn't room in
     *            the database.
     * @param elem
     *            The element to save
     * @param sequence
     *            The sequence number for the saved element
     * @param delayable
     *            Whether or not the save operation can be delayed.
     * @throws Exception
     *             If there is an error saving the element.
     */
    public void persistQueueElement(QueueDescriptor descriptor, ISourceController<?> controller, V elem, long sequence, boolean delayable) throws Exception;

    /**
     * Tests whether or not the given element is persistent. When a message is
     * added to a persistent queue it should be saved via
     * {@link #persistQueueElement(QueueDescriptor, Object, long, boolean)}
     * 
     * @param elem
     *            The element to check.
     * @return True if the element requires persistence.
     */
    public boolean isElemPersistent(V elem);

    /**
     * Tests whether or not the given element came from the store. If so, a
     * queue must delete the element when it is finished with it
     * 
     * @param elem
     *            The element to check.
     * @return True if the element came from the store.
     */
    public boolean isFromStore(V elem);

    /**
     * Adds a queue to the store.
     * 
     * @param queue
     */
    public void addQueue(QueueDescriptor queue);

    /**
     * Deletes a queue from the store.
     * 
     * @param queue
     */
    public void deleteQueue(QueueDescriptor queue);

}
