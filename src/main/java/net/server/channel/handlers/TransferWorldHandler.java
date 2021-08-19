/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.server.channel.handlers;

import client.MapleCharacter;
import client.MapleClient;
import config.YamlConfig;
import net.AbstractMaplePacketHandler;
import net.server.Server;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

import java.sql.*;

/**
 *
 * @author Ronan
 * @author Ubaware
 */
public final class TransferWorldHandler extends AbstractMaplePacketHandler {
    
    @Override
    public final void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        slea.readInt(); //cid
        int birthday = slea.readInt();
        if (!CashOperationHandler.checkBirthday(c, birthday)) {
            c.announce(PacketCreator.showCashShopMessage((byte) 0xC4));
            c.announce(PacketCreator.enableActions());
            return;
        }
        MapleCharacter chr = c.getPlayer();
        if(!YamlConfig.config.server.ALLOW_CASHSHOP_WORLD_TRANSFER || Server.getInstance().getWorldsSize() <= 1) {
            c.announce(PacketCreator.sendWorldTransferRules(9, c));
            return;
        }
        int worldTransferError = chr.checkWorldTransferEligibility();
        if(worldTransferError != 0) {
            c.announce(PacketCreator.sendWorldTransferRules(worldTransferError, c));
            return;
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT completionTime FROM worldtransfers WHERE characterid=?")) {
            ps.setInt(1, chr.getId());
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                Timestamp completedTimestamp = rs.getTimestamp("completionTime");
                if(completedTimestamp == null) { //has pending world transfer
                    c.announce(PacketCreator.sendWorldTransferRules(6, c));
                    return;
                } else if(completedTimestamp.getTime() + YamlConfig.config.server.WORLD_TRANSFER_COOLDOWN > System.currentTimeMillis()) {
                    c.announce(PacketCreator.sendWorldTransferRules(7, c));
                    return;
                }
            }
        } catch(SQLException e) {
            e.printStackTrace();
            return;
        }
        c.announce(PacketCreator.sendWorldTransferRules(0, c));
    }
}