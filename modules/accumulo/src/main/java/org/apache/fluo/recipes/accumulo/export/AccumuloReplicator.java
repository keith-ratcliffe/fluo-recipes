/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.fluo.recipes.accumulo.export;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.fluo.api.data.Bytes;
import org.apache.fluo.api.data.Column;
import org.apache.fluo.recipes.core.export.SequencedExport;
import org.apache.fluo.recipes.core.transaction.LogEntry;
import org.apache.fluo.recipes.core.transaction.TxLog;

/**
 * An {@link AccumuloExporter} that replicates data to Accumulo using a {@link TxLog}
 */
public class AccumuloReplicator extends AccumuloExporter<String, TxLog> {

  @Override
  protected Collection<Mutation> translate(SequencedExport<String, TxLog> export) {
    return generateMutations(export.getSequence(), export.getValue());
  }

  /**
   * Returns LogEntry filter for Accumulo replication
   */
  public static Predicate<LogEntry> getFilter() {
    return le -> le.getOp().equals(LogEntry.Operation.DELETE)
        || le.getOp().equals(LogEntry.Operation.SET);
  }

  /**
   * Generates Accumulo mutations from a Transaction log. Used to Replicate Fluo table to Accumulo.
   *
   * @param txLog Transaction log
   * @param seq Export sequence number
   * @return Collection of mutations
   */
  public static Collection<Mutation> generateMutations(long seq, TxLog txLog) {
    Map<Bytes, Mutation> mutationMap = new HashMap<>();
    for (LogEntry le : txLog.getLogEntries()) {
      LogEntry.Operation op = le.getOp();
      Column col = le.getColumn();
      byte[] cf = col.getFamily().toArray();
      byte[] cq = col.getQualifier().toArray();
      byte[] cv = col.getVisibility().toArray();
      if (op.equals(LogEntry.Operation.DELETE) || op.equals(LogEntry.Operation.SET)) {
        Mutation m = mutationMap.computeIfAbsent(le.getRow(), k -> new Mutation(k.toArray()));
        if (op.equals(LogEntry.Operation.DELETE)) {
          if (col.isVisibilitySet()) {
            m.putDelete(cf, cq, new ColumnVisibility(cv), seq);
          } else {
            m.putDelete(cf, cq, seq);
          }
        } else {
          if (col.isVisibilitySet()) {
            m.put(cf, cq, new ColumnVisibility(cv), seq, le.getValue().toArray());
          } else {
            m.put(cf, cq, seq, le.getValue().toArray());
          }
        }
      }
    }
    return mutationMap.values();
  }
}
