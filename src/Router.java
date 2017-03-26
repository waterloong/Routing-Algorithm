import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class Router {

    private static final int NUMBER_OF_ROUTERS = 5;
    private int id;
    private DatagramSocket localSocket;
    private DatagramSocket nseSocket;
    private InetAddress nseHost;
    private int nsePort;
    private CircuitDb circuitDb;
    private PrintWriter logWriter;
    private Map<Integer, Map<Integer, Integer>> topologyDatabase = new HashMap<>();

    public Router(int id, InetAddress nseHost, int nsePort, int routerPort) throws IOException {
        this.logWriter = new PrintWriter("router" + id + ".log");
        this.localSocket = new DatagramSocket(routerPort);
        this.nseSocket = new DatagramSocket(0);
        this.id = id;
        this.nseHost = nseHost;
        this.nsePort = nsePort;
        sendInit();
        receiveCircuitDb();
        sendHello();
        receiveHello();
    }

    private void sendInit() throws IOException {
        PacketInit packetInit = new PacketInit();
        packetInit.routerId = id;
        byte[] data = packetInit.toBytes();
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
        nseSocket.send(datagramPacket);
        System.out.println("R" + id +" sends an INIT: router_id " + id);
    }

    private byte[] receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        this.localSocket.receive(packet);
        System.out.println(new String(packet.getData()));
        return packet.getData();
    }

    private void receiveCircuitDb() throws IOException {
        byte[] data = this.receivePacket();
        for (byte b: data) {
            System.out.println(b);
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        this.circuitDb = new CircuitDb();
        circuitDb.nLinks = Integer.reverseBytes(byteBuffer.getInt());
        System.out.println("R" + circuitDb.nLinks + " receives a CIRCUIT_DB: nLinks " + circuitDb.nLinks);
        circuitDb.linkCosts = new LinkCost[circuitDb.nLinks];
        for (LinkCost linkCost: circuitDb.linkCosts) {
            linkCost.link = Integer.reverseBytes(byteBuffer.getInt());
            linkCost.cost = Integer.reverseBytes(byteBuffer.getInt());
            //System.out.printf("R%d -> R%d link %d cost %d\n", id, id, linkCost.link, linkCost.cost);
        }
    }

    private void sendHello() throws IOException {
        PacketHello packetHello = new PacketHello();
        packetHello.routerId = id;
        for (LinkCost linkCost: circuitDb.linkCosts) {
            packetHello.link = linkCost.link;
            byte[] data = packetHello.toBytes();
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
            nseSocket.send(datagramPacket);
            System.out.println("R" + id + " sends an Hello: router_id " + id + " link_id " + linkCost.link);
        }
    }

    private void receiveHello() throws IOException {
        while (true) {
            byte[] data = this.receivePacket();
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);
            PacketHello packetHello = new PacketHello();
            packetHello.routerId = Integer.reverseBytes(byteBuffer.getInt());
            packetHello.link = Integer.reverseBytes(byteBuffer.getInt());
            System.out.println("R" + id + " receives a HELLO: router_id " + packetHello.routerId + " link_id " + packetHello.link);
        }
    }

    /**
     * @param args • <router_id> is an integer that represents the router id. It should be unique for each router.
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
    }

    private static class CircuitDb {
        private int nLinks;
        private LinkCost[] linkCosts;
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
        private int sender, router_id, link_id, cost, via;
    }
}

