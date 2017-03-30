import javax.swing.tree.DefaultMutableTreeNode;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class Router {

    private static final int INF = 65535;
    private static final int NUMBER_OF_ROUTERS = 5;
    private static final int DELAY = 30000; // program automatic termination time
    private int id; // id of this router
    private DatagramSocket nseSocket;
    private InetAddress nseHost;
    private int nsePort;
    private CircuitDb[] circuitDbs = new CircuitDb[NUMBER_OF_ROUTERS]; // topology database
    // track if a circuit_db entry has been sent to a link already, key: linkId as "sent content", value: links via which key is sent
    private Map<Integer, List<Integer>> duplicateTracker = new HashMap<>();
    private PrintWriter logWriter;

    /**
     * release resources
     */
    private void close() {
        if (logWriter != null) {
            logWriter.close();
        }
    }

    private void printTopologyDatabase() {
        logWriter.println("# Topology Database");
        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            CircuitDb circuitDb = circuitDbs[i];
            if (circuitDb.nLinks == 0) continue;
            logWriter.printf("R%d -> R%d nbr link %d\n", id, i + 1, circuitDb.nLinks);
            for (LinkCost linkCost : circuitDb.linkCosts) {
                logWriter.printf("R%d -> R%d link %d cost %d\n", id, i + 1, linkCost.link, linkCost.cost);
            }
        }
        dijkstra(); // only compute shortest path if topology database has changed
    }

    private void dijkstra() {
        // initialize the graph & RIB
        int[] newRib = new int[NUMBER_OF_ROUTERS]; // value is cost to this router
        Map<Integer, Set<int[]>> adjacencyGraph = new HashMap<>(); // routerId -> list of (routerId, cost)
        Map<Integer, Integer> linkToRouterMap = new HashMap<>(); // linkId -> routerId

        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            newRib[i] = INF;
            adjacencyGraph.put(i, new LinkedHashSet<>());
        }
        newRib[id - 1] = 0;

        // fill the graph with edges
        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            for (LinkCost lc : circuitDbs[i].linkCosts) {
                int link = lc.link;
                if (linkToRouterMap.containsKey(link)) {
                    int j = linkToRouterMap.get(link);
                    // connect nodes
                    adjacencyGraph.get(i).add(new int[]{j, lc.cost});
                    adjacencyGraph.get(j).add(new int[]{i, lc.cost});
                    if (i == id - 1) {
                        newRib[j] = lc.cost; // adjacent to the source
                    } else if (j == id - 1) {
                        newRib[i] = lc.cost; // adjacent to the source
                    }
                } else {
                    linkToRouterMap.put(link, i);
                }
            }
        }
        // build path tree
        DefaultMutableTreeNode pathTreeRoot = new DefaultMutableTreeNode(id - 1); // need to write my own tree class when have time
        DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[NUMBER_OF_ROUTERS]; // keep track of all tree nodes
        Set<DefaultMutableTreeNode> p = new LinkedHashSet<>();
        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            if (i == id - 1) {
                nodes[i] = pathTreeRoot;
            } else {
                nodes[i] = new DefaultMutableTreeNode(i);
                nodes[i].setParent(pathTreeRoot);
                p.add(nodes[i]);
            }
        }
        while (!p.isEmpty()) {
            // find w not in p such that D(w) is a minimum
            p.stream().sorted((a, b) -> newRib[(Integer) a.getUserObject()] - newRib[(Integer) b.getUserObject()])
                    .limit(1)
                    .forEach(w -> {
                        p.remove(w);
                        // update D(v) for all v adjacent to w and not in p :
                        int routerId = (int) w.getUserObject();
                        for (int[] neighbor : adjacencyGraph.get(routerId)) {
                            int neighborId = neighbor[0];
                            int linkCost = neighbor[1];
                            if (linkCost + newRib[routerId] < newRib[neighborId]) {
                                nodes[neighborId].setParent(w);
                                newRib[neighborId] = linkCost + newRib[routerId];
                            }
                        }
                    });
        }

        // print RIB
        logWriter.println("# RIB");
        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            logWriter.printf("R%d -> R%d -> ", id, i + 1);
            if (i + 1 == id) {
                logWriter.println("local, 0"); // self
            } else if (newRib[i] == INF){
                logWriter.println("INF, INF"); // unconnected router
            } else {
                logWriter.printf("R%d, %d\n", (int)(nodes[i].getUserObjectPath()[1]) + 1, newRib[i]);
            }
        }

    }

    public Router(int id, InetAddress nseHost, int nsePort, int routerPort) throws IOException {
        this.logWriter = new PrintWriter(new FileOutputStream("router" + id + ".log"), true); // automatically flushed
        this.nseSocket = new DatagramSocket(routerPort);
        this.id = id;
        this.nseHost = nseHost;
        this.nsePort = nsePort;
        for (int i = 0; i < NUMBER_OF_ROUTERS; i++) {
            circuitDbs[i] = new CircuitDb();
        }
        sendInit();
        receiveCircuitDb();
        sendHello();
        receiveHelloAndDatabase();
    }

    private void sendInit() throws IOException {
        PacketInit packetInit = new PacketInit();
        packetInit.routerId = id;
        byte[] data = packetInit.toBytes();
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
        nseSocket.send(datagramPacket);
        logWriter.println("R" + id + " sends an INIT: router_id " + id);
    }

    private byte[] receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        this.nseSocket.receive(packet);
        return packet.getData();
    }

    private void receiveCircuitDb() throws IOException {
        byte[] data = this.receivePacket();
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        circuitDbs[id - 1].nLinks = Integer.reverseBytes(byteBuffer.getInt());
        logWriter.println("R" + id + " receives a CIRCUIT_DB: nLinks " + circuitDbs[id - 1].nLinks);
        for (int i = 0; i < circuitDbs[id - 1].nLinks; i++) {
            LinkCost linkCost = new LinkCost(Integer.reverseBytes(byteBuffer.getInt()), Integer.reverseBytes(byteBuffer.getInt()));
            circuitDbs[id - 1].linkCosts.add(linkCost);
            logWriter.printf("R%d -> R%d link %d cost %d\n", id, id, linkCost.link, linkCost.cost);
        }
    }

    private void sendHello() throws IOException {
        PacketHello packetHello = new PacketHello();
        packetHello.routerId = id;
        for (LinkCost linkCost : circuitDbs[id - 1].linkCosts) {
            packetHello.link = linkCost.link;
            byte[] data = packetHello.toBytes();
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
            nseSocket.send(datagramPacket);
            logWriter.println("R" + id + " sends a Hello: router_id " + id + " link_id " + linkCost.link);
        }
    }

    private void receiveHelloAndDatabase() throws IOException {
        // exit after some time
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.exit(0);
            }
        }, DELAY);
        // receive new packet after receiving circuit database and sending hello
        while (true) {
            byte[] data = this.receivePacket();
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);
            boolean isHello = byteBuffer.getInt(8) == 0; // hello has 8 bytes, LS PDU has 20 bytes
            byteBuffer.position(0);
            if (isHello) {
                // receive hello
                PacketHello packetHello = new PacketHello();
                packetHello.routerId = Integer.reverseBytes(byteBuffer.getInt());
                packetHello.link = Integer.reverseBytes(byteBuffer.getInt());
                logWriter.println("R" + id + " receives a HELLO: routerId " + packetHello.routerId + " linkId " + packetHello.link);

                int routerId = 1;
                while (routerId <= NUMBER_OF_ROUTERS) {
                    for (LinkCost linkCost : circuitDbs[routerId - 1].linkCosts) {
                        byte[] bufferedLspdu = new PacketLSPDU(id, routerId, linkCost.link, linkCost.cost, packetHello.link).toBytes();
                        DatagramPacket datagramPacket = new DatagramPacket(bufferedLspdu, bufferedLspdu.length, nseHost, nsePort);
                        this.nseSocket.send(datagramPacket);
                        logWriter.printf("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                                id, id, routerId, linkCost.link, linkCost.cost, packetHello.link);
                        this.duplicateTracker.computeIfAbsent(linkCost.link, ArrayList::new).add(packetHello.link);
                    }
                    routerId ++;
                }
            } else {
                // received LSPDU
                int sender = Integer.reverseBytes(byteBuffer.getInt());
                int routerId = Integer.reverseBytes(byteBuffer.getInt());
                int linkId = Integer.reverseBytes(byteBuffer.getInt());
                int cost = Integer.reverseBytes(byteBuffer.getInt());
                int via = Integer.reverseBytes(byteBuffer.getInt());
                logWriter.printf("R%d receives an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                        id, sender, routerId, linkId, cost, via);

                LinkCost linkCost = new LinkCost(linkId, cost);
                if (this.circuitDbs[routerId - 1].linkCosts.add(linkCost)) {
                    this.circuitDbs[routerId - 1].nLinks++;
                    printTopologyDatabase();
                }
                List<Integer> sent = this.duplicateTracker.computeIfAbsent(linkId, ArrayList::new);
                sent.add(via);
                for (LinkCost lc : circuitDbs[id - 1].linkCosts) {
                    PacketLSPDU packetLSPDU = new PacketLSPDU(id, routerId, linkId, cost, lc.link);
                    if (!sent.contains(lc.link)) {
                        byte[] bufferedLspdu = packetLSPDU.toBytes();
                        DatagramPacket datagramPacket = new DatagramPacket(bufferedLspdu, bufferedLspdu.length, nseHost, nsePort);
                        this.nseSocket.send(datagramPacket);
                        logWriter.printf("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                                id, id, routerId, linkId, cost, lc.link);
                        sent.add(lc.link);
                    }
                }
            }
        }

    }

    /**
     * @param args • <routerId> is an integer that represents the router id. It should be unique for each router.
     *             • <nse_host> is the host where the Network State Emulator is running.
     *             • <nse_port> is the port number of the Network State Emulator.
     *             • <router_port> is the router port
     */
    public static void main(String[] args) throws Exception {
        // write your code here
        Router router = null;
        try {
            if (args.length != 4) throw new IllegalArgumentException();
            int id = Integer.parseInt(args[0]);
            InetAddress nseHost = InetAddress.getByName(args[1]);
            int nsePort = Integer.parseInt(args[2]);
            int routerPort = Integer.parseInt(args[3]);
            router = new Router(id, nseHost, nsePort, routerPort);
        } catch (IllegalArgumentException iae) {
            System.err.println("Invalid arguments.");
            iae.printStackTrace();
            System.exit(1);
        } finally {
            if (router != null) {
                router.close();
            }
        }
    }

    private static class PacketInit {

        private int routerId;

        private byte[] toBytes() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.putInt(Integer.reverseBytes(routerId));
            return byteBuffer.array();
        }
    }

    private static class LinkCost {
        private int link, cost;

        public LinkCost(int link, int cost) {
            this.link = link;
            this.cost = cost;
        }

        @Override
        public boolean equals(Object linkCost) {
            return linkCost instanceof LinkCost && this.link == ((LinkCost) linkCost).link;
        }

        @Override
        public int hashCode() {
            return link;
        }
    }

    private static class CircuitDb {
        private int nLinks;
        private Set<LinkCost> linkCosts = new LinkedHashSet<>();
    }

    private static class PacketHello {

        private int routerId, link;

        private byte[] toBytes() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(8);
            byteBuffer.putInt(Integer.reverseBytes(routerId));
            byteBuffer.putInt(Integer.reverseBytes(link));
            return byteBuffer.array();
        }
    }

    private static class PacketLSPDU {
        private int sender, routerId, linkId, cost, via;

        private PacketLSPDU(int sender, int routerId, int linkId, int cost, int via) {
            this.sender = sender;
            this.routerId = routerId;
            this.linkId = linkId;
            this.cost = cost;
            this.via = via;
        }

        private byte[] toBytes() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(20);
            byteBuffer.putInt(Integer.reverseBytes(sender));
            byteBuffer.putInt(Integer.reverseBytes(routerId));
            byteBuffer.putInt(Integer.reverseBytes(linkId));
            byteBuffer.putInt(Integer.reverseBytes(cost));
            byteBuffer.putInt(Integer.reverseBytes(via));
            return byteBuffer.array();
        }
    }
}