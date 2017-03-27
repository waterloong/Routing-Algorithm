import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class Router {

    private static final int NUMBER_OF_ROUTERS = 5;
    private int id;
    private DatagramSocket nseSocket;
    private InetAddress nseHost;
    private int nsePort;
    private CircuitDb[] circuitDbs = new CircuitDb[NUMBER_OF_ROUTERS];
    private int[][] dag = new int[NUMBER_OF_ROUTERS][NUMBER_OF_ROUTERS];
    // track if a circuit_db entry has been sent to a link already
    private Map<Integer, LinkCost> tracker = new HashMap<>();
    private PrintWriter logWriter;

    public void printTopologyDatabase() {
        System.out.println("# Topology Database");
        for (int i = 0; i < NUMBER_OF_ROUTERS; i ++) {
            CircuitDb circuitDb = circuitDbs[i];
            if (circuitDb == null) continue;
            System.out.printf("R%d -> R%d nbr link %d", id, i + 1, circuitDb.nLinks);
            for (LinkCost linkCost: circuitDb.linkCosts) {
                System.out.printf("R%d -> R%d link %d cost %d\n", id, linkCost.link, linkCost.cost);
            }
        }
    }

    public Router(int id, InetAddress nseHost, int nsePort, int routerPort) throws IOException {
        this.logWriter = new PrintWriter("router" + id + ".log");
        this.nseSocket = new DatagramSocket(routerPort);
        this.id = id;
        this.nseHost = nseHost;
        this.nsePort = nsePort;
        for (int i = 0; i < NUMBER_OF_ROUTERS; i ++) {
            Arrays.fill(dag[i], Integer.MAX_VALUE);
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
        System.out.println("R" + id +" sends an INIT: routerId " + id);
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
        this.circuitDbs[id - 1] = new CircuitDb();
        circuitDbs[id - 1].nLinks = Integer.reverseBytes(byteBuffer.getInt());
        System.out.println("R" + circuitDbs[id - 1].nLinks + " receives a CIRCUIT_DB: nLinks " + circuitDbs[id - 1].nLinks);
        for (int i = 0; i < circuitDbs[id - 1].nLinks; i ++) {
            LinkCost linkCost = new LinkCost(Integer.reverseBytes(byteBuffer.getInt()), Integer.reverseBytes(byteBuffer.getInt()));
            circuitDbs[id - 1].linkCosts.add(linkCost);
            System.out.printf("R%d -> R%d link %d cost %d\n", id, id, linkCost.link, linkCost.cost);
        }
    }

    private void sendHello() throws IOException {
        PacketHello packetHello = new PacketHello();
        packetHello.routerId = id;
        for (LinkCost linkCost: circuitDbs[id - 1].linkCosts) {
            packetHello.link = linkCost.link;
            byte[] data = packetHello.toBytes();
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
            nseSocket.send(datagramPacket);
            System.out.println("R" + id + " sends an Hello: routerId " + id + " linkId " + linkCost.link);
        }
    }

    private void receiveHelloAndDatabase() throws IOException {
        while (true) {
            byte[] data = this.receivePacket();
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);

            if (byteBuffer.limit() == 8) {
                PacketHello packetHello = new PacketHello();
                packetHello.routerId = Integer.reverseBytes(byteBuffer.getInt());
                packetHello.link = Integer.reverseBytes(byteBuffer.getInt());
                System.out.println("R" + id + " receives a HELLO: routerId " + packetHello.routerId + " linkId " + packetHello.link);
                int routerId = 1;
                while (routerId <= NUMBER_OF_ROUTERS) {
                    if (circuitDbs[routerId - 1] != null) {
                        for (LinkCost linkCost : circuitDbs[routerId - 1].linkCosts) {
                            byte[] bufferedLspdu = new PacketLSPDU(id, routerId, linkCost.link, linkCost.cost, packetHello.link).toBytes();
                            DatagramPacket datagramPacket = new DatagramPacket(bufferedLspdu, bufferedLspdu.length, nseHost, nsePort);
                            this.nseSocket.send(datagramPacket);
                        }
                    }
                    routerId ++;
                }
            } else {
                int sender = byteBuffer.getInt();
                int routerId = byteBuffer.getInt();
                int linkId = byteBuffer.getInt();
                int cost = byteBuffer.getInt();
                int via = byteBuffer.getInt();
                System.out.printf("R%d receives an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                        id, sender, routerId, linkId, cost, via);

                this.circuitDbs[routerId - 1].nLinks ++;
                LinkCost linkCost = new LinkCost(linkId, cost);
                this.circuitDbs[routerId - 1].linkCosts.add(linkCost);
                this.tracker.put(linkId, linkCost);
                for (LinkCost lc: circuitDbs[id - 1].linkCosts) {
                    if (!tracker.containsKey(linkId)) {
                        PacketLSPDU packetLSPDU = new PacketLSPDU(id, routerId, linkId, cost, lc.link);
                        byte[] bufferedLspdu = packetLSPDU.toBytes();
                        DatagramPacket datagramPacket = new DatagramPacket(bufferedLspdu, bufferedLspdu.length, nseHost, nsePort);
                        this.nseSocket.send(datagramPacket);
                        System.out.printf("R%d receives an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                                id, id, routerId, linkId, cost, lc.link);
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
        try {
            if (args.length != 4) throw new IllegalArgumentException();
            int id = Integer.parseInt(args[0]);
            InetAddress nseHost = InetAddress.getByName(args[1]);
            int nsePort = Integer.parseInt(args[2]);
            int routerPort = Integer.parseInt(args[3]);
            new Router(id, nseHost, nsePort, routerPort);
        } catch (IllegalArgumentException iae) {
            System.err.println("Invalid arguments.");
            System.exit(1);
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

    private static class LinkCost
    {
        private int link, cost;

        public LinkCost(int link, int cost) {
            this.link = link;
            this.cost = cost;
        }

        @Override
        public boolean equals(Object linkCost) {
            return linkCost instanceof LinkCost && this.link == ((LinkCost)linkCost).link;
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

