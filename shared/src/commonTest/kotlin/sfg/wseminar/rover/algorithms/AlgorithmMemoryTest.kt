package sfg.wseminar.rover.algorithms

import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.generator.*
import kotlin.test.*

class AlgorithmMemoryTest {
    @Test fun peakTracksSearchWorkingSetIndependentlyOfRetainedFrames() {
        val graph = GraphGenerator.generate(GraphGenerationOptions(seed = 42))
        fun run(algorithm: RoverAlgorithm, retain: Boolean): Long {
            val timeline = AlgorithmRunner.createTimeline(algorithm, graph, graph.nodes.first(), graph.nodes.last(), retainHistory = retain)
            var previous = 0L
            while (!timeline.isExhausted) {
                timeline.generateNext()
                assertTrue(timeline.peakAlgorithmBytes >= previous)
                previous = timeline.peakAlgorithmBytes
            }
            assertTrue(previous > 0)
            assertTrue(previous < timeline.estimatedBytes)
            return previous
        }
        val dijkstra = run(Dijkstra, true)
        assertEquals(dijkstra, run(Dijkstra, false))
        assertTrue(run(AStar, false) > dijkstra)
        assertEquals(run(GoToWaypoint, true), run(GoToWaypoint, false))
    }
}
