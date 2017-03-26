import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class Router {

    private int id;
    private DatagramSocket localSocket;
    private DatagramSocket nseSocket;
    private InetAddress nseHost;
    private int nsePort;

    public Router(int id, InetAddress nseHost, int nsePort, int routerPort) throws IOException {
        this.localSocket = new DatagramSocket(routerPort);
        this.nseSocket = new DatagramSocket(0);
        this.id = id;
        this.nseHost = nseHost;
        this.nsePort = nsePort;
        sendInit();
        receivePacket();
    }

    private void sendInit() throws IOException {
        PacketInit packetInit = new PacketInit();
        packetInit.routerId = id;
        byte[] data = packetInit.toBytes();
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, nseHost, nsePort);
        for (byte b : datagramPacket.getData()) {
            System.out.println(b);
        }
        nseSocket.send(datagramPacket);
    }

    private byte[] receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        this.localSocket.receive(packet);
        System.out.println(new String(packet.getData()));
        return packet.getData();
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

        int routerId;

        private byte[] toBytes() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.putInt(Integer.reverseBytes(routerId));
            return byteBuffer.array();
        }
    }
}

