
// This file is part of TOOL, a robotics interaction and development
// package created by the Northern Bites RoboCup team of Bowdoin College
// in Brunswick, Maine.
//
// TOOL is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// TOOL is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with TOOL.  If not, see <http://www.gnu.org/licenses/>.

package edu.bowdoin.robocup.TOOL.Net;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


import edu.bowdoin.robocup.TOOL.TOOL;
import edu.bowdoin.robocup.TOOL.TOOLException;
import edu.bowdoin.robocup.TOOL.Data.DataSource;
import edu.bowdoin.robocup.TOOL.Data.Frame;
import edu.bowdoin.robocup.TOOL.Data.File.FileSet;
import edu.bowdoin.robocup.TOOL.Data.File.FrameLoader;
import edu.bowdoin.robocup.TOOL.Image.ThresholdedImage;
import edu.bowdoin.robocup.TOOL.Image.TOOLImage;

/**
 * A RemoteRobot object represents an advanced DataSet layered over a network
 * connection to a remote host.  It will have many options, but is backed
 * (currently) by a file set.  As it is dynamic, requests for data 'off the end'
 * of the DataSet will cause a request for new data to be requested and
 * retreived from the remote host.<br><br>
 *
 * Currently, only image retrieval is implemeneted on both sides, but we plan
 * to include accessibility for virtually all type of information from the
 * robot, and advanced controls over which data to retrieve each frame.<br><br>
 *
 * Also needing to be implemented is advanced memory handling.  This may be the
 * job of the underlying FileSet, not the RemoteRobot.  Currently all frames
 * are kept in memory unsaved until explicitly stored.  No memory-collection
 * and management features have been written.  The extra space in the Data
 * module and manager may be a great place for this.  In fact I'm copying this
 * comment there.
 */

public class RemoteRobot extends FileSet {

    public static final String NAME_UNKNOWN = "Unkown";
    public static final String ADDR_UNKNOWN = "0.0.0.0";

    private TOOLProtocol proto;
    private DataRequest requestDetails;

    public RemoteRobot(DataSource src, int i, String storagePath, String host,
            InetAddress addr) {
        super(src, i, storagePath);

        proto = new TOOLProtocol(addr);
        requestDetails = DataRequest.IMAGE_JOINTS;

        init(host, addr.getHostAddress(), 0);
    }

    public boolean isConnected() {
        return proto.isConnected();
    }

    public DataRequest getRequestDetails() {
        return requestDetails;
    }

    //
    // DataSet methods
    //

    public boolean autoSave() {
        return true;
    }
    
    public boolean dynamic() {
        return true;
    }

    public Frame get(int i) {
        if (i >= size())
            // need to create a new Frame
            return add(false, false);
        else
            // return the existing frame
            return super.get(i);
    }
    
    public void load(int i) throws TOOLException {
        Frame f = get(i);
        if (f.loaded())
            return;

        if (!proto.isConnected())
            connect();

        proto.request(requestDetails);

        if (requestDetails.info()) {
            setName(proto.getRobotName());
            setDesc(proto.getHost());
        }

        f.setType(proto.getRobotType());
        f.setRobot(proto.getRobotName());

        Vector<Float> v;

        if (requestDetails.joints())
            f.setJoints(Arrays.asList(proto.getJoints()));

        if (requestDetails.sensors())
            f.setSensors(Arrays.asList(proto.getSensors()));

        if (requestDetails.image())
            f.setImage(FrameLoader.loadBytes(proto.getRobotType(),
                proto.getImage()));

        // Mark as loaded
        frameLoaded.set(f.index(), true);
        // Mark as changed, as we have not written to file yet
        frameChanged.set(f.index(), true);
    }

    public void connect() throws TOOLException {
        if (proto.isConnected())
            return;

        proto.initConnection();

        if (proto.hasInfo()) {
            setName(proto.getRobotName());
            setDesc(proto.getHost());
            NetworkModule.message(NetworkModule.class,
                "Robot " + proto.getRobotName() +
                " is type " + proto.getRobotDef());
        }else if (proto.isConnected()) {
            setName(NAME_UNKNOWN);
            setDesc(ADDR_UNKNOWN);
        }else
            NetworkModule.raiseError(NetworkModule.class,
                "Could not connect to remote robot");
    }

    public void disconnect() {
        TOOL.CONSOLE.message("Disconnecting from " + name());
        proto.disconnect();
    }

    public TOOLImage retrieveImage() {
        try {
            if (!proto.isConnected())
                connect();
            proto.request(DataRequest.IMAGE_ONLY);
            return FrameLoader.loadBytes(proto.getRobotType(),
                proto.getImage());
        }catch (TOOLException e) {
            NetworkModule.logError("Attempt to retreive direct image failed",
                                   e);
            return null;
        }
    }

    public ThresholdedImage retrieveThresh() {
        try {
            if (!proto.isConnected())
                connect();
            proto.request(DataRequest.THRESH_ONLY);
            return new ThresholdedImage(proto.getThresh(), 
                    proto.getRobotDef().imageWidth(),
                    proto.getRobotDef().imageHeight());
        }catch (TOOLException e) {
            NetworkModule.logError("Attempt to retreive direct image failed",
                                   e);
            return null;
        }
    }

}
