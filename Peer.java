import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Represents a single peer to which I am connected too for a single file..
 */
public class Peer {
    private static final Logger LOGGER = Logger
            .getLogger(Logger.GLOBAL_LOGGER_NAME);
    private boolean optimisticallyUnchoked = false;

    public boolean isOptimisticallyUnchoked(){
        return  optimisticallyUnchoked;
    }

    public void setOptimisticallyUnchoked(boolean status){
        optimisticallyUnchoked = status;
    }

    private boolean client = false;

    public static List<Peer> interestedNeighboursinMe = Collections.synchronizedList(new ArrayList<Peer>());//new PriorityBlockingQueue<Peer>(
//            10, );

    public static Map<Integer, Peer> notInterestedNeighboursinMe = Collections.synchronizedMap(new HashMap<Integer, Peer>());

    public static Map<Integer, Peer> peersChokedMeMap = Collections.synchronizedMap(new HashMap<Integer, Peer>());

    public static Map<Integer, Peer> peersUnchokedMeMap = Collections.synchronizedMap(new HashMap<Integer, Peer>());

    public static int myId = 0;
    /**
     * Map object between peer id and piece requested time.
     */
    public static Map<Integer, Long> requestTime = Collections.synchronizedMap(new HashMap<Integer, Long>());

    /**
     * Map object between peer id and download time.
     */
    public static Map<Integer, Long> downloadTime = Collections.synchronizedMap(new ConcurrentHashMap<Integer, Long>());

    /**
     * Downloading Rate from this peer. Initially set to 0.
     */
    private long downloadingRate = 0;

    public synchronized long getDownloadingRate() {
        return downloadingRate;
    }

    public synchronized void setDownloadingRate(final long d) {
        downloadingRate = -d;
    }

    public void setClient(final boolean v) {
        client = v;
    }

    public boolean isClient() {
        return client;
    }

    // All global variables are data representing the peer running in the
    // current machine that is shared with it's connecting peers.

    /**
     * Map to store the peers with which handshake has been made successfully.
     * Global entity.
     */
    private static Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();

    private HashSet<Peer> set = new HashSet<Peer>(); // Set of Peers that have
    // shown interest in my
    // data.


    private int id;

    private int requestedIndex;

    public synchronized  void setRequestedIndex(final int i) {
        requestedIndex = i;
    }

    public static synchronized boolean isPresentInInterestedMeList(Peer peerConnected){
        for(Peer p : Peer.interestedNeighboursinMe){
            if(p.getId() == peerConnected.getId()){
                return true;
            }
        }
        return false;
    }

    public static synchronized void removeFromInterestedInMeList(Peer peerConnected){
        Peer.interestedNeighboursinMe.remove(peerConnected);
    }

    public static synchronized void addInInterestedInMeList(Peer peerConnected){
        Peer.interestedNeighboursinMe.add(peerConnected);
    }

    public synchronized  int getRequestedIndex() {
        return requestedIndex;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Bitfields corresponding to a data file that is being shared.
     */

    private final static byte[] globadlBitfield;

    public static byte[] getGlobadlBitfield() {
        return globadlBitfield;
    }

    private final static byte[] myBitfield;

    public static synchronized byte[] getMyBitField() {
        return myBitfield;
    }


    private Boolean initialized = false;

    public boolean isInitialized(){
        return initialized;
    }
    public synchronized void initialized() {
        while (initialized == false) {
            try {
                //System.out.println("waiting state ");
                wait(1000);
            } catch (Exception e) {
                //System.out.println("exception while waiting " + e.getMessage());
            }
        }
    }

    public synchronized  void setInitialized(boolean status){
        initialized = true;
        notify();
    }

    /**
     * Bit field already requested.
     */
    private final static byte[] bitFieldRequested;

    public static synchronized byte[] getBitFieldRequested() {
        return bitFieldRequested;
    }

    public static synchronized void setMyBitFieldIndx(int index, int i) {
        //int in = index;
        //int pos = i;
        myBitfield[index] |= (1 << (7 - i));
    }

    public synchronized void setPieceIndxRequested(int index, int indexFromRight) {
        //int in = index;
        //int pos = indexFromRight;
        bitFieldRequested[index] |= (1 << indexFromRight);
        //Byte b = new Byte(bitFieldRequested[index]);
            //System.out.println("b.intValue() = " + b.intValue());
    }

    public static synchronized  void resetPieceIndexRequested(int index, int indexFromLeft) {
        //int in = index;
        //int pos = indexFromLeft;
        bitFieldRequested[index] &= ~(1 << (7 - indexFromLeft));
        // Byte b = new Byte(bitFieldRequested[index]);
           // System.out.println("b.intValue() = " + b.intValue());
    }

    /**
     * Peers bit field message.
     */
    private byte[] peerBitFieldMsg = null;

    public synchronized byte[]  getPeerBitFieldMsg(){
        return peerBitFieldMsg;
    }
    public static byte[] dataShared = null;

    // static block
    static {
        String fileName = Configuration.getComProp().get("FileName");
        String fileSizeStr = Configuration.getComProp().get("FileSize");
        Integer fileSize = Integer.parseInt(fileSizeStr);
        long noOfPieces = 0;
        int pieceSize = Integer.parseInt(Configuration.getComProp().get(
                "PieceSize"));

        if (fileSize % pieceSize == 0) {
            noOfPieces = fileSize / pieceSize;
        } else {
            noOfPieces = fileSize / pieceSize + 1;
        }
        double bl = Math.ceil(noOfPieces / 8.0f);
        myBitfield = new byte[(int) bl];
        globadlBitfield = new byte[(int) bl];

        bitFieldRequested = new byte[(int) bl];
        dataShared = new byte[Integer.parseInt(Configuration.getComProp().get("FileSize"))];
        File f = new File(fileName);
        if (f.exists()) // This peer is the original uploader..
        {
            if (f.length() != fileSize) {
                //System.out.println("The file size mentioned in common cfg is "
                  //      + fileSize);
                //System.out.println("Actual file size is " + f.length());
                System.exit(-1);
            } else {
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(f);
                    fileInputStream.read(dataShared);
                    fileInputStream.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
            if (noOfPieces % 8.0 == 0) {
                Arrays.fill(myBitfield, (byte) 255);
                Arrays.fill(globadlBitfield, (byte) 255);
            } else {
                int numOfBitsToBeSetTo1InLastByte = (int) noOfPieces % 8;
                Arrays.fill(myBitfield, (byte) 255); // set all to 1
                Arrays.fill(globadlBitfield, (byte) 255);
                myBitfield[myBitfield.length - 1] = 0; // set last byte to 0
                globadlBitfield[globadlBitfield.length - 1] = 0;
                while (numOfBitsToBeSetTo1InLastByte != 0) {
                    myBitfield[myBitfield.length - 1] |= (1 << (8 - numOfBitsToBeSetTo1InLastByte));
                    globadlBitfield[globadlBitfield.length - 1] |= (1 << (8 - numOfBitsToBeSetTo1InLastByte));
                    numOfBitsToBeSetTo1InLastByte--;
                }
            }
        }
        else {
            if (noOfPieces % 8.0 == 0) {
                Arrays.fill(globadlBitfield, (byte) 255);
            } else {
                int numOfBitsToBeSetTo1InLastByte = (int) noOfPieces % 8;
                Arrays.fill(globadlBitfield, (byte) 255); // set all to 1
                globadlBitfield[globadlBitfield.length - 1] = 0; // set last byte to 0
                while (numOfBitsToBeSetTo1InLastByte != 0) {
                    globadlBitfield[globadlBitfield.length - 1] |= (1 << (8 - numOfBitsToBeSetTo1InLastByte));
                    numOfBitsToBeSetTo1InLastByte--;
                }
            }
        }

    }

    private Socket socket = null;
    private OutputStream out = null;
    private InputStream in = null;

   /*
     * BufferedReader buffReader = new BufferedReader(new
    * InputStreamReader(socket.getInputStream())); ){ String input, output;
    * while((input = buffReader.readLine()) != null){
    * System.out.println(input); if(input.equals("Bye")) break; }
    */

    public Peer(Socket s) {
        this.socket = s;
        try {
            out = new BufferedOutputStream(socket.getOutputStream());
            in = new BufferedInputStream(socket.getInputStream());
        } catch (IOException e) {
            //System.out.println("socket exception!!!");
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void finalize() {
        this.close();
    }

    public synchronized void sendHandshakeMsg() {
        synchronized (map) {
            // now send handshake to this peer
            byte[] concatenateByteArrays = Util.concatenateByteArrays(Util
                    .concatenateByteArrays(Constants.HANDSHAKE_HEADER,
                            Constants.ZERO_BITS), Configuration.getComProp()
                    .get("peerId").getBytes());
            try {
                out.write(concatenateByteArrays);
                out.flush();
                map.put(id, false);
            } catch (IOException e) {
                LOGGER.severe("Send handshake failed !! " + e.getMessage());
                e.printStackTrace();
            }
        }

    }

    public synchronized int receiveHandshakeMsg() {
        try {
            byte[] b = new byte[32];
            in.read(b);
            byte[] copyOfRange = Arrays.copyOfRange(b, 28, 32);
            String peer = new String(copyOfRange);
            Integer peerId = Integer.parseInt(peer);
            //System.out.println("The peer id is " + peerId);
            if (client) {
                // then verify the expected neighbour
                if (map.containsKey(peerId) && map.get(peerId) == false) {
                    map.put(peerId, true);
                    //System.out.println("verified for id " + peerId);
                } else {
                    //System.out.println("Screwed!!!");
                }
            }
            return peerId;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
    }

    // Sends a Message of type Bitfiled
    public synchronized void sendBitfieldMsg() {
        try {
            byte[] myBitField = getMyBitField();
            byte[] actualMessage = MessagesUtil.getActualMessage(myBitField,
                    Constants.ActualMessageTypes.BITFIELD);
            out.write(actualMessage);
            out.flush();
        } catch (IOException e) {
            //System.out.println("Bitfield message sending failed!!!");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public synchronized  void updatePeerBitFieldMsg(int pieceIndex) {
        int posi = 7 - (pieceIndex % 8);
        peerBitFieldMsg[pieceIndex / 8] |= (1 << posi);
    }

    public synchronized void readPeerBitfieldMsg() {
        peerBitFieldMsg = MessagesUtil.readActualMessage(in,
                Constants.ActualMessageTypes.BITFIELD);
    }

    public void print(String s) {
        //System.out.println(s);
    }

    // TODO: Test this function
    // logger in thread
    public synchronized boolean isInterested() {
        // me xor peer
        // result & ~me
        // if not 0 then interested
        int i = 0;
        byte[] myBitField = getMyBitField();
        print("My bit field is " + Arrays.toString(myBitField));
        print("Peers bit field is " + Arrays.toString(peerBitFieldMsg));
        byte[] result = new byte[myBitField.length];
        for (byte byt : myBitField) {
            result[i] = (byte) (byt ^ peerBitFieldMsg[i]);
            i++;
        }
        i = 0;

        for (byte b : myBitField) {

            result[i] = (byte) (result[i] & ~b);
            if (result[i] != 0) {
                return true;
            }
        }
        return false;
    }

    // Sends a Message of type Interesed
    public synchronized void sendInterestedMsg() {
        ////System.out.println("Sending interested message ");
        byte[] actualMessage = MessagesUtil
                .getActualMessage(Constants.ActualMessageTypes.INTERESTED);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends a Message of type NotInterested
    public synchronized void sendNotInterestedMsg() {
        byte[] actualMessage = MessagesUtil
                .getActualMessage(Constants.ActualMessageTypes.NOT_INTERESTED);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends a Message of type Have
    public synchronized void sendHaveMsg(int pieceIndex) {
        byte[] actualMessage = MessagesUtil.getActualMessage(
                Util.intToByteArray(pieceIndex),
                Constants.ActualMessageTypes.HAVE);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends a Message of type Choke
    public synchronized  void sendChokeMsg() {
        byte[] actualMessage = MessagesUtil
                .getActualMessage(Constants.ActualMessageTypes.CHOKE);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends a Message of type UnChoke
    public synchronized void sendUnChokeMsg() {
        byte[] actualMessage = MessagesUtil
                .getActualMessage(Constants.ActualMessageTypes.UNCHOKE);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean choked = true;

    public synchronized void setChoked(boolean n) {
        choked = n;
    }

    public synchronized boolean isChoked() {
        return choked;
    }

    // Sends a Message of type Request
    public synchronized void sendRequestMsg(int pieceIndex) {
        if (pieceIndex >= 0) {
            byte[] pieceIndexByteArray = Util.intToByteArray(pieceIndex);

            byte[] actualMessage = MessagesUtil.getActualMessage(
                    pieceIndexByteArray, Constants.ActualMessageTypes.REQUEST);
            try {
                out.write(actualMessage);
                out.flush();
                // Noting the time when the request was made
                Peer.requestTime.put(id, System.nanoTime());
            } catch (IOException e) {
                //System.out.println("io exception in reading " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Sends a Message of type Piece
    public synchronized void sendPieceMsg(int pieceIndex) {
        int pI = pieceIndex;
        int stdPieceSize = Integer.parseInt(Configuration.getComProp().get(
                "PieceSize"));
        int startIndex = stdPieceSize * pieceIndex;
        int endIndex = startIndex + stdPieceSize - 1;
        if (endIndex >= dataShared.length) {
            endIndex = dataShared.length - 1;
        }
        //special case
        //if pieceSize is greater than the entire file left

        byte[] data = new byte[endIndex - startIndex + 1 + 4]; // 4 is for pieceIndex

        //populate the piece index
        byte[] pieceIndexByteArray = Util.intToByteArray(pieceIndex);
        for (int i = 0; i < 4; i++) {
            data[i] = pieceIndexByteArray[i];
        }
        //populates the actual data
        int i = startIndex; // plus 4 again for piece index
        for (; i <= endIndex /*&& i < dataShared.length*/; i++) {
            data[i - startIndex + 4] = dataShared[i];
        }

        byte[] actualMessage = MessagesUtil.getActualMessage(data,
                Constants.ActualMessageTypes.PIECE);
        try {
            //System.out.println("The actual message size is " + actualMessage.length);
            out.write(actualMessage);
            out.flush();
        } catch (IOException e) {
            //System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // TODO: Test this function
   /* public synchronized int getNextBitFieldIndexToRequest() {
        // request a piece that I do not have and have not requested from other
        // neighbors, selection
        // of piece should happen randomly
        byte[] reqstdUntilnow = getBitFieldRequested();
        byte[] ntHavendNtReqst = new byte[peerBitFieldMsg.length]; // to store bytes that I don't have
        byte[] bitFieldReqAndHave = new byte[peerBitFieldMsg.length];
//        System.out.println("peerBitfield length " + peerBitFieldMsg.length);
//        System.out.println("reqstdUntilnow[reqstdUntilnow.length - 1] = " + reqstdUntilnow[reqstdUntilnow.length - 1]);
//        System.out.println("ntHavendNtReqst[ntHavendNtReqst.length - 1] = " + ntHavendNtReqst[ntHavendNtReqst.length - 1]);
//        System.out.println("bitFieldReqAndHave[bitFieldReqAndHave.length - 1] = " + bitFieldReqAndHave[bitFieldReqAndHave.length - 1]);
        byte[] mybitfield = getMyBitField();
        //System.out.println("Arrays.toString(reqstdUntilnow) = " + Arrays.toString(reqstdUntilnow));
        for (int i = 0; i < reqstdUntilnow.length; i++) {
            bitFieldReqAndHave[i] = (byte) (reqstdUntilnow[i] | mybitfield[i]);
        }
//        System.out.println("bitFieldReqAndHave[bitFieldReqAndHave.length - 1] = " + bitFieldReqAndHave[bitFieldReqAndHave.length - 1]);
        // determine bits I dont have.
        for (int i = 0; i < bitFieldReqAndHave.length; i++) {
            ntHavendNtReqst[i] = (byte) ((bitFieldReqAndHave[i] ^ peerBitFieldMsg[i]) & ~bitFieldReqAndHave[i]);
        }
        //System.out.println("Arrays.toString(peerBitFieldMsg) = " + Arrays.toString(peerBitFieldMsg));
        //System.out.println("Arrays.toString(getMyBitField()) = " + Arrays.toString(getMyBitField()));
        //System.out.println("Arrays.toString(bitFieldReqAndHave) = " + Arrays.toString(bitFieldReqAndHave));
        //System.out.println("Arrays.toString(ntHavendNtReqst) = " + Arrays.toString(ntHavendNtReqst));
        //System.out.println("ntHavendNtReqst[ntHavendNtReqst.length - 1] = " + ntHavendNtReqst[ntHavendNtReqst.length - 1]);
        //System.out.println("myBitfield[myBitfield.length - 1] = " + myBitfield[myBitfield.length - 1]);
        //System.out.println("peerBitFieldMsg[peerBitFieldMsg.length - 1] = " + peerBitFieldMsg[peerBitFieldMsg.length - 1]);

        int count = 0;
        int pos = 0;
        for (int i = 0; i < ntHavendNtReqst.length; i++) {
            count = 8 * i;
            byte temp = ntHavendNtReqst[i];
            Byte b = new Byte(temp);

            pos = 0;
            while (temp != 0 && pos < 8) {
                if ((temp & (1 << pos)) != 0) {
                    setPieceIndxRequested(i, pos);
                    pos = 7 - pos;
                    int index = count + pos;
                    setRequestedIndex(index);
                    // set the ith bit as 1
                    return index;
                }
                ++pos;
            }
        }

        System.out.println("Arrays.toString(myBitField) = " + Arrays.toString(getMyBitField()));
        return -1;
    }*/

    // TODO: Test this function
    public synchronized int getNextBitFieldIndexToRequest() {
        // request a piece that I do not have and have not requested from other
        // neighbors, selection
        // of piece should happen randomly
        byte[] reqstdUntilnow = getBitFieldRequested();
        byte[] ntHavendNtReqst = new byte[peerBitFieldMsg.length]; // to store bytes that I don't have
        byte[] bitFieldReqAndHave = new byte[peerBitFieldMsg.length];
//        System.out.println("peerBitfield length " + peerBitFieldMsg.length);
//        System.out.println("reqstdUntilnow[reqstdUntilnow.length - 1] = " + reqstdUntilnow[reqstdUntilnow.length - 1]);
//        System.out.println("ntHavendNtReqst[ntHavendNtReqst.length - 1] = " + ntHavendNtReqst[ntHavendNtReqst.length - 1]);
//        System.out.println("bitFieldReqAndHave[bitFieldReqAndHave.length - 1] = " + bitFieldReqAndHave[bitFieldReqAndHave.length - 1]);
        byte[] mybitfield = getMyBitField();
        //System.out.println("Arrays.toString(reqstdUntilnow) = " + Arrays.toString(reqstdUntilnow));
        for (int i = 0; i < reqstdUntilnow.length; i++) {
            bitFieldReqAndHave[i] = (byte) (reqstdUntilnow[i] & mybitfield[i]);
        }
//        System.out.println("bitFieldReqAndHave[bitFieldReqAndHave.length - 1] = " + bitFieldReqAndHave[bitFieldReqAndHave.length - 1]);
        // determine bits I dont have.
        for (int i = 0; i < bitFieldReqAndHave.length; i++) {
            ntHavendNtReqst[i] = (byte) ((bitFieldReqAndHave[i] ^ peerBitFieldMsg[i]) & ~bitFieldReqAndHave[i]);
        }
        //System.out.println("Arrays.toString(peerBitFieldMsg) = " + Arrays.toString(peerBitFieldMsg));
        //System.out.println("Arrays.toString(getMyBitField()) = " + Arrays.toString(getMyBitField()));
        //System.out.println("Arrays.toString(bitFieldReqAndHave) = " + Arrays.toString(bitFieldReqAndHave));
        //System.out.println("Arrays.toString(ntHavendNtReqst) = " + Arrays.toString(ntHavendNtReqst));
        //System.out.println("ntHavendNtReqst[ntHavendNtReqst.length - 1] = " + ntHavendNtReqst[ntHavendNtReqst.length - 1]);
        //System.out.println("myBitfield[myBitfield.length - 1] = " + myBitfield[myBitfield.length - 1]);
        //System.out.println("peerBitFieldMsg[peerBitFieldMsg.length - 1] = " + peerBitFieldMsg[peerBitFieldMsg.length - 1]);

        ArrayList<Integer> byteList = new ArrayList<>();
        for (int i = 0; i < ntHavendNtReqst.length; i++) {
            byte temp = ntHavendNtReqst[i];
            if(temp != 0){
                byteList.add(i);
            }
        }

        if (byteList.isEmpty()) {
            return -1;
        }
        int i = byteList.get(ThreadLocalRandom.current().nextInt(0, byteList.size()));
        int pos = ThreadLocalRandom.current().nextInt(0, 8);
        byte temp = ntHavendNtReqst[i];
        while(temp == 0 || (temp & (1 << pos)) == 0){
            i = ThreadLocalRandom.current().nextInt(0, ntHavendNtReqst.length);
            pos = ThreadLocalRandom.current().nextInt(0, 8);
            temp = ntHavendNtReqst[i];
        }
        setPieceIndxRequested(i, pos);
        pos = 7 - pos;
        int count = i*8;
        int index = count + pos;
        setRequestedIndex(index);
        return index;

    }
}
