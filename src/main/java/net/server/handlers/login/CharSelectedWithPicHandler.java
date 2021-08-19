package net.server.handlers.login;

import client.MapleClient;
import net.AbstractMaplePacketHandler;
import net.server.Server;
import net.server.coordinator.session.Hwid;
import net.server.coordinator.session.SessionCoordinator;
import net.server.coordinator.session.SessionCoordinator.AntiMulticlientResult;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.PacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class CharSelectedWithPicHandler extends AbstractMaplePacketHandler {
    private static final Logger log = LoggerFactory.getLogger(CharSelectedWithPicHandler.class);

    private static int parseAntiMulticlientError(AntiMulticlientResult res) {
        return switch (res) {
            case REMOTE_PROCESSING -> 10;
            case REMOTE_LOGGEDIN -> 7;
            case REMOTE_NO_MATCH -> 17;
            case COORDINATOR_ERROR -> 8;
            default -> 9;
        };
    }
    
    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        String pic = slea.readMapleAsciiString();
        int charId = slea.readInt();
        
        String macs = slea.readMapleAsciiString();
        String hostString = slea.readMapleAsciiString();

        final Hwid hwid;
        try {
            hwid = Hwid.fromHostString(hostString);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid host string: {}", hostString, e);
            c.announce(PacketCreator.getAfterLoginError(17));
            return;
        }
        
        c.updateMacs(macs);
        c.updateHwid(hwid);

        if (c.hasBannedMac() || c.hasBannedHWID()) {
            SessionCoordinator.getInstance().closeSession(c, true);
            return;
        }
        
        Server server = Server.getInstance();
        if(!server.haveCharacterEntry(c.getAccID(), charId)) {
            SessionCoordinator.getInstance().closeSession(c, true);
            return;
        }
        
        if (c.checkPic(pic)) {
            c.setWorld(server.getCharacterWorld(charId));
            World wserv = c.getWorldServer();
            if(wserv == null || wserv.isWorldCapacityFull()) {
                c.announce(PacketCreator.getAfterLoginError(10));
                return;
            }
            
            String[] socket = server.getInetSocket(c, c.getWorld(), c.getChannel());
            if(socket == null) {
                c.announce(PacketCreator.getAfterLoginError(10));
                return;
            }
            
            AntiMulticlientResult res = SessionCoordinator.getInstance().attemptGameSession(c, c.getAccID(), hwid);
            if (res != AntiMulticlientResult.SUCCESS) {
                c.announce(PacketCreator.getAfterLoginError(parseAntiMulticlientError(res)));
                return;
            }
            
            server.unregisterLoginState(c);
            c.setCharacterOnSessionTransitionState(charId);
            
            try {
                c.announce(PacketCreator.getServerIP(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]), charId));
            } catch (UnknownHostException | NumberFormatException e) {
                e.printStackTrace();
            }
        } else {
            c.announce(PacketCreator.wrongPic());
        }
    }
}
