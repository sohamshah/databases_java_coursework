package edu.berkeley.cs186.database.concurrency;

import java.util.*;

/**
 * A waits for graph for the lock manager (used to detect if
 * deadlock will occur and throw a DeadlockException if it does).
 */
public class WaitsForGraph {

  // We store the directed graph as an adjacency list where each node (transaction) is
  // mapped to a list of the nodes it has an edge to.
  private Map<Long, ArrayList<Long>> graph;
  private Boolean hasCycle;

  public WaitsForGraph() {
    graph = new HashMap<Long, ArrayList<Long>>();
    hasCycle = false;
  }

  public boolean containsNode(long transNum) {
    return graph.containsKey(transNum);
  }

  protected void addNode(long transNum) {
    if (!graph.containsKey(transNum)) {
      graph.put(transNum, new ArrayList<Long>());
    }
  }

  protected void addEdge(long from, long to) {
    if (!this.edgeExists(from, to)) {
      ArrayList<Long> edges = graph.get(from);
      edges.add(to);
    }
  }

  protected void removeEdge(long from, long to) {
    if (this.edgeExists(from, to)) {
      ArrayList<Long> edges = graph.get(from);
      edges.remove(to);
    }
  }

  protected boolean edgeExists(long from, long to) {
    if (!graph.containsKey(from)) {
      return false;
    }
    ArrayList<Long> edges = graph.get(from);
    return edges.contains(to);
  }

  /**
   * Checks if adding the edge specified by to and from would cause a cycle in this
   * WaitsForGraph. Does not actually modify the graph in any way.
   * @param from the transNum from which the edge points
   * @param to the transNum to which the edge points
   * @return
   */
  protected boolean edgeCausesCycle(long from, long to) {
    Boolean from_wasnt_there = false;
    if (!this.containsNode(from)) {
      this.addNode(from);
      from_wasnt_there = true;
    }
    this.addEdge(from, to);

    this.hasCycle = false;
    for (Long vertex : this.graph.keySet()) {
      if (!this.graph.get(vertex).isEmpty()) {
        Set<Long> visited = new HashSet<Long>();
        Set<Long> onStack = new HashSet<Long>();
        findCycle(vertex, visited, onStack);
        if (this.hasCycle) {
          break;
        }
      }
    }
    this.removeEdge(from, to);
    if (from_wasnt_there) {
      this.graph.remove(from);
    }
    return this.hasCycle;
  }

  //approach from stackoverflow is used here : http://stackoverflow.com/questions/19113189/detecting-cycles-in-a-graph-using-dfs-2-different-approaches-and-whats-the-dif

  private void findCycle(long root, Set<Long> visited, Set<Long> onStack) {
    visited.add(root);
    onStack.add(root);
    for (Long neighbor : this.graph.get(root)) {
      if (!visited.contains(neighbor)) {
        if (this.graph.containsKey(neighbor)) {
          findCycle(neighbor, visited, onStack);
        } else {
          visited.add(neighbor);
          return;
        }
      } else if (onStack.contains(neighbor)) {
        this.hasCycle = true;
        return;
      }
    }

    onStack.remove(root);
  }
}
