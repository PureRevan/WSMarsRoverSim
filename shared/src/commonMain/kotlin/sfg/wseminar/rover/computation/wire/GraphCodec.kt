package sfg.wseminar.rover.computation.wire

import sfg.wseminar.rover.graph.Graph

fun PacketWriter.graph(graph: Graph) {
    int(graph.startNodeId ?: -1); int(graph.goalNodeId ?: -1); int(graph.seed); int(graph.generatorVersion)
    int(graph.nodes.size)
    graph.nodes.forEach { double(it.x); double(it.y); string(it.label) }
    int(graph.edges.size)
    graph.edges.forEach { int(it.from.id); int(it.to.id); double(it.weight); bool(it.bidirectional); string(it.label) }
}
fun PacketReader.graph(): Graph {
    val start = int(); val goal = int(); val seed = int(); val version = int()
    val nodes = List(count()) { id -> Graph.Node(double(), double(), id, string()) }
    val edges = List(count()) { id -> Graph.Edge(nodes[int()], nodes[int()], double(), id, bool(), string()) }
    return Graph(nodes, edges, start.takeIf { it >= 0 }, goal.takeIf { it >= 0 }, seed, version)
}
