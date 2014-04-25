

import threading
import Queue
import copy
import time

from ossie.utils import uuid
from bulkio.statistics import InStats
from bulkio import sri
from ossie.cf.CF import Port

from bulkio.bulkioInterfaces import BULKIO, BULKIO__POA #@UnusedImport 


class InPort:
    DATA_BUFFER=0
    TIME_STAMP=1
    END_OF_STREAM=2
    STREAM_ID=3
    SRI=4
    SRI_CHG=5
    QUEUE_FLUSH=6
    _TYPE_ = 'c'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None,  maxsize=100, PortTransferType=_TYPE_ ):
        self.name = name
        self.logger = logger
        self.queue = Queue.Queue(maxsize)
        self.port_lock = threading.Lock()
        self.stats =  InStats(name, PortTransferType)
        self.blocking = False
        self.sri_cmp = sriCompare
        self.newStreamCallback = newStreamCallback
        self.sriDict = {} # key=streamID, value=StreamSRI

        _cmpMsg  = "DEFAULT"
        _sriMsg  = "EMPTY"
        if sriCompare != sri.compare:
            _cmpMsg  = "USER_DEFINED"
        if newStreamCallback:
            _sriMsg  = "USER_DEFINED"

        if self.logger:
            self.logger.debug( "bulkio::InPort CTOR port:" + str(name) + 
                          " Blocking/MaxInputQueueSize " + str(self.blocking) + "/"  + str(maxsize) + 
                          " SriCompare/NewStreamCallback " +  _cmpMsg + "/" + _sriMsg );

    def setNewStreamListener(self, newStreamCallback):
        self.port_lock.acquire()
        try:
            self.newStreamCallback = newStreamCallback
        finally:
            self.port_lock.release()

    def enableStats(self, enabled):
        self.stats.setEnabled(enabled)

    def _get_statistics(self):
        self.port_lock.acquire()
        try:
            recStat = self.stats.retrieve()
        finally:
            self.port_lock.release()
        return recStat

    def _get_state(self):
        self.port_lock.acquire()
        try:
            full = self.queue.full()
            empty = self.queue.empty()
        finally:
            self.port_lock.release()
        if full:
            return BULKIO.BUSY
        elif empty:
            return BULKIO.IDLE
        else:
            return BULKIO.ACTIVE

    def _get_activeSRIs(self):
        self.port_lock.acquire()
        try:
            activeSRIs = [self.sriDict[entry][0] for entry in self.sriDict]
        finally:
            self.port_lock.release()
        return activeSRIs

    def getCurrentQueueDepth(self):
        self.port_lock.acquire()
        try:
            depth = self.queue.qsize()
        finally:
            self.port_lock.release()
        return depth

    def getMaxQueueDepth(self):
        self.port_lock.acquire()
        try:
            depth = self.queue.maxsize
        finally:
            self.port_lock.release()
        return depth
        
    #set to -1 for infinite queue
    def setMaxQueueDepth(self, newDepth):
        self.port_lock.acquire()
        try:
            self.queue.maxsize = int(newDepth)
        finally:
            self.port_lock.release()

    def pushSRI(self, H):
        
        if self.logger:
            self.logger.trace( "bulkio::InPort pushSRI ENTER (port=" + str(self.name) +")" )

        self.port_lock.acquire()
        try:
            if H.streamID not in self.sriDict:
                if self.logger:
                    self.logger.debug( "pushSRI PORT:" + str(self.name) + " NEW SRI:" + str(H.streamID) )
                if self.newStreamCallback:
                    self.newStreamCallback( H ) 
                self.sriDict[H.streamID] = (copy.deepcopy(H), True)
                if H.blocking:
                    self.blocking = True
            else:
                sri, sriChanged = self.sriDict[H.streamID]
                if self.sri_cmp:
                    if not self.sri_cmp(sri, H):
                        self.sriDict[H.streamID] = (copy.deepcopy(H), True)
                        if H.blocking:
                            self.blocking = True
        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.trace( "bulkio::InPort pushSRI EXIT (port=" + str(self.name) +")" )


    def pushPacket(self, data, T, EOS, streamID):

        if self.logger:
            self.logger.trace( "bulkio::InPort pushPacket ENTER (port=" + str(self.name) +")" )

        self.port_lock.acquire()
        try:
            if self.queue.maxsize == 0:
                if self.logger:
                    self.logger.trace( "bulkio::InPort pushPacket EXIT (port=" + str(self.name) +")" )
                return
            packet = None
            sri = BULKIO.StreamSRI(1, 0.0, 1.0, 1, 0, 0.0, 0.0, 0, 0, streamID, False, [])
            sriChanged = False
            if self.sriDict.has_key(streamID):
                sri, sriChanged = self.sriDict[streamID]
                self.sriDict[streamID] = (sri, False)

            if self.blocking:
                packet = (data, T, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                self.stats.update(len(data), float(self.queue.qsize()) / float(self.queue.maxsize), streamID, False)
                self.queue.put(packet)
            else:
                sriChangedHappened = False
                if self.queue.full():
                    try:
                        if self.logger:
                            self.logger.debug( "bulkio::InPort pushPacket PURGE INPUT QUEUE (SIZE=" + 
                                          str(len(self.queue.queue)) + ")"  )

                        self.queue.mutex.acquire()
                        while len(self.queue.queue) != 0:
                            data, T, EOS, streamID, sri, sriChanged, inputQueueFlushed = self.queue.queue.pop()
                            if sriChanged:
                                sriChangedHappened = True
                                self.queue.queue.clear()
                        self.queue.mutex.release()
                    except Queue.Empty:
                        self.queue.mutex.release()
                    if sriChangedHappened:
                        sriChanged = True
                    packet = (data, T, EOS, streamID, copy.deepcopy(sri), sriChanged, True)
                    self.stats.update(len(data), float(self.queue.qsize()) / float(self.queue.maxsize), EOS, streamID, True)
                else:
                    packet = (data, T, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                    self.stats.update(len(data), float(self.queue.qsize()) / float(self.queue.maxsize), EOS, streamID, False)

                if self.logger:
                    self.logger.trace( "bulkio::InPort pushPacket NEW Packet (QUEUE=" + 
                                       str(len(self.queue.queue)) + ")" )
                self.queue.put(packet)
        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.trace( "bulkio::InPort pushPacket EXIT (port=" + str(self.name) +")" )
    
    def getPacket(self):

        if self.logger:
            self.logger.trace( "bulkio::InPort getPacket ENTER (port=" + str(self.name) +")" )

        try:
            data, T, EOS, streamID, sri, sriChanged, inputQueueFlushed = self.queue.get(block=False)
            
            if EOS: 
                if self.sriDict.has_key(streamID):
                    (a,b) = self.sriDict.pop(streamID)
                    if sri.blocking:
                        stillBlock = False
                        for _sri, _sriChanged in self.sriDict.values():
                            if _sri.blocking:
                                stillBlock = True
                                break
                        if not stillBlock:
                            self.blocking = False
            return (data, T, EOS, streamID, sri, sriChanged, inputQueueFlushed)
        except Queue.Empty:
            return None, None, None, None, None, None, None

        if self.logger:
            self.logger.trace( "bulkio::InPort getPacket EXIT (port=" + str(self.name) +")" )



class InCharPort(InPort, BULKIO__POA.dataChar):
    _TYPE_ = 'c'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InCharPort._TYPE_ )

class InOctetPort(InPort, BULKIO__POA.dataOctet):
    _TYPE_ = 'B'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InOctetPort._TYPE_ )

class InShortPort(InPort, BULKIO__POA.dataShort):
    _TYPE_ = 'h'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InShortPort._TYPE_ )

class InUShortPort(InPort, BULKIO__POA.dataUshort):
    _TYPE_ = 'H'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InUShortPort._TYPE_ )

class InLongPort(InPort, BULKIO__POA.dataLong):
    _TYPE_ = 'i'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InLongPort._TYPE_ )

class InULongPort(InPort, BULKIO__POA.dataUlong):
    _TYPE_ = 'I'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InULongPort._TYPE_ )

class InLongLongPort(InPort, BULKIO__POA.dataLongLong):
    _TYPE_ = 'q'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InLongLongPort._TYPE_ )


class InULongLongPort(InPort, BULKIO__POA.dataUlongLong):
    _TYPE_ = 'Q'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InULongLongPort._TYPE_ )


class InFloatPort(InPort, BULKIO__POA.dataFloat):
    _TYPE_ = 'f'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InFloatPort._TYPE_ )


class InDoublePort(InPort, BULKIO__POA.dataDouble):
    _TYPE_ = 'd'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InDoublePort._TYPE_ )



class InFilePort(InPort, BULKIO__POA.dataFile):
    _TYPE_ = 'd'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InFilePort._TYPE_ )


    def pushPacket(self, URL, T, EOS, streamID):

        if self.logger:
            self.logger.trace( "bulkio::InFilePort pushPacket ENTER (port=" + str(self.name) +")" )

        self.port_lock.acquire()
        try:
            if self.queue.maxsize == 0:
                if self.logger:
                    self.logger.trace( "bulkio::InFilePort pushPacket EXIT (port=" + str(self.name) +")" )
                return
            packet = None
            sri = BULKIO.StreamSRI(1, 0.0, 1.0, 1, 0, 0.0, 0.0, 0, 0, streamID, False, [])
            sriChanged = False
            if self.sriDict.has_key(streamID):
                sri, sriChanged = self.sriDict[streamID]
                self.sriDict[streamID] = (sri, False)

            if self.blocking:
                packet = (URL, T, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                self.stats.update(1, float(self.queue.qsize()) / float(self.queue.maxsize), EOS,streamID, False)
                self.queue.put(packet)
            else:
                sriChangedHappened = False
                if self.queue.full():
                    try:
                        if self.logger:
                            self.logger.debug( "bulkio::InFilePort pushPacket PURGE INPUT QUEUE (SIZE=" + 
                                          str(len(self.queue.queue)) + ")"  )

                        self.queue.mutex.acquire()
                        while len(self.queue.queue) != 0:
                            URL, T, EOS, streamID, sri, sriChanged, inputQueueFlushed = self.queue.queue.pop()
                            if sriChanged:
                                sriChangedHappened = True
                                self.queue.queue.clear()
                        self.queue.mutex.release()
                    except Queue.Empty:
                        self.queue.mutex.release()
                    if sriChangedHappened:
                        sriChanged = True
                    packet = (URL, T, EOS, streamID, copy.deepcopy(sri), sriChanged, True)
                    self.stats.update(1, float(self.queue.qsize()) / float(self.queue.maxsize), EOS, streamID, True)
                else:
                    packet = (URL, T, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                    self.stats.update(len(URL), float(self.queue.qsize()) / float(self.queue.maxsize), streamID, False)

                if self.logger:
                    self.logger.trace( "bulkio::InXMLPort pushPacket NEW Packet (QUEUE=" + 
                                       str(len(self.queue.queue)) + ")" )
                self.queue.put(packet)
        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.trace( "bulkio::InFilePort pushPacket EXIT (port=" + str(self.name) +")" )


class InXMLPort(InPort, BULKIO__POA.dataXML):
    _TYPE_ = 'd'
    def __init__(self, name, logger=None, sriCompare=sri.compare, newStreamCallback=None, maxsize=100 ):
        InPort.__init__(self, name, logger, sriCompare, newStreamCallback, maxsize, InXMLPort._TYPE_ )


    def pushPacket(self, xml_string, EOS, streamID):

        if self.logger:
            self.logger.trace( "bulkio::InXMLPort pushPacket ENTER (port=" + str(self.name) +")" )
            
        self.port_lock.acquire()
        try:
            if self.queue.maxsize == 0:
                if self.logger:
                    self.logger.trace( "bulkio::InXMLPort pushPacket EXIT (port=" + str(self.name) +")" )
                return
            packet = None
            sri = BULKIO.StreamSRI(1, 0.0, 1.0, 1, 0, 0.0, 0.0, 0, 0, streamID, False, [])
            sriChanged = False
            if self.sriDict.has_key(streamID):
                sri, sriChanged = self.sriDict[streamID]
                self.sriDict[streamID] = (sri, False)

            if self.blocking:
                packet = (xml_string, None, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                self.stats.update(len(xml_string), float(self.queue.qsize()) / float(self.queue.maxsize), streamID, False)
                self.queue.put(packet)
            else:
                sriChangedHappened = False
                if self.queue.full():
                    try: 
                        if self.logger:
                            self.logger.debug( "bulkio::InXMLPort pushPacket PURGE INPUT QUEUE (SIZE=" + 
                                          str(len(self.queue.queue)) + ")"  )
                        self.queue.mutex.acquire()
                        while len(self.queue.queue) != 0:
                            xml_string, T, EOS, streamID, sri, sriChanged, inputQueueFlushed = self.queue.queue.pop()
                            if sriChanged:
                                sriChangedHappened = True
                                self.queue.queue.clear()
                        self.queue.mutex.release()
                    except Queue.Empty:
                        self.queue.mutex.release()
                    if sriChangedHappened:
                        sriChanged = True
                    packet = (xml_string, None, EOS, streamID, copy.deepcopy(sri), sriChanged, True)
                    self.stats.update(len(xml_string), float(self.queue.qsize()) / float(self.queue.maxsize), EOS, streamID, True)
                else:
                    packet = (xml_string, None, EOS, streamID, copy.deepcopy(sri), sriChanged, False)
                    self.stats.update(len(xml_string), float(self.queue.qsize()) / float(self.queue.maxsize), EOS, streamID, False)

                if self.logger:
                    self.logger.trace( "bulkio::InXMLPort pushPacket NEW Packet (QUEUE=" + 
                                       str(len(self.queue.queue)) + ")" )
                self.queue.put(packet)
        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.trace( "bulkio::InXMLPort pushPacket EXIT (port=" + str(self.name) +")" )
    
class InAttachablePort:
    _TYPE_='b'
    def __init__(self, name, logger=None, attachDetachCallback=None, sriCmp=None, timeCmp=None, PortType = _TYPE_, newStreamCallback=None,interface=None):
        self.name = name
        self.logger = logger
        self.port_lock = threading.Lock()
        self._attachedStreams = {} # key=attach_id, value = (streamDef, userid) 
        self.stats = InStats(name, PortType )
        self.sriDict = {} # key=streamID, value=(StreamSRI, PrecisionUTCTime)
        self.attachDetachCallback = attachDetachCallback
        self.newStreamCallback = newStreamCallback
        self.sri_cmp = sriCmp
        self.time_cmp = timeCmp
        self.sriChanged = False
        if not interface:
            if self.logger:
                self.logger.error("InAttachablePort __init__ - an interface must be specified, set to BULKIO.dataSDDS or BULKIO.dataVITA49")
            raise Port.InvalidPort(1, "InAttachablePort __init__ - an interface must be specified, set to BULKIO.dataSDDS or BULKIO.dataVITA49")
        self.interface=interface # BULKIO port interface (valid options are BULKIO.dataSDDS or BULKIO.dataVITA49)
        self.setNewAttachDetachListener(newStreamCallback)
        if self.logger:
            self.logger.debug("bulkio::InAttachablePort CTOR port:" + str(self.name) + " using interface " + str(self.interface))

    def setNewAttachDetachListener(self, attachDetachCallback ):
        self.port_lock.acquire()
        try:
            self.attachDetachCallback = attachDetachCallback

            # Set _attach_cb
            try:
                self._attach_cb = getattr(attachDetachCallback, "attach")
                if not callable(self._attach_cb):
                    self._attach_cb = None
            except AttributeError:
                self._attach_cb = None
            
            # Set _detach_cb
            try:
                self._detach_cb = getattr(attachDetachCallback, "detach")
                if not callable(self._detach_cb):
                    self._detach_cb = None
            except AttributeError:
                self._detach_cb = None

        finally:
            self.port_lock.release()

    def setNewStreamListener(self, newStreamCallback):
        self.port_lock.acquire()
        try:
            self.newStreamCallback = newStreamCallback
        finally:
            self.port_lock.release()

    def setBitSize(self, bitSize):
        self.stats.setBitSize(bitSize)

    def enableStats(self, enabled):
        self.stats.setEnabled(enabled)
        
    def updateStats(self, elementsReceived, queueSize, streamID):
        self.port_lock.acquire()
        try:
            self.stats.update(elementsReceived, queueSize, streamID)
        finally:
            self.port_lock.release()

    def _get_statistics(self):
        self.port_lock.acquire()
        try:
            recStat = self.stats.retrieve()
        finally:
            self.port_lock.release()
        return recStat

    def _get_state(self):
        self.port_lock.acquire()
        try:
            numAttachedStreams = len(self._attachedStreams.values())
        finally:
            self.port_lock.release()
        if numAttachedStreams == 0:
            return BULKIO.IDLE
        # default behavior is to limit to one connection
        elif numAttachedStreams == 1:
            return BULKIO.BUSY
        else:
            return BULKIO.ACTIVE

    def _get_attachedSRIs(self):
        sris = []
        self.port_lock.acquire()
        try:
            for entry in self.sriDict:
                sris.append(copy.deepcopy(self.sriDict[entry]))
        finally:
            self.port_lock.release()
        return sris

    def _get_usageState(self):
        self.port_lock.acquire()
        try:
            numAttachedStreams = len(self._attachedStreams.values())
        finally:
            self.port_lock.release()
        if numAttachedStreams == 0:
            return self.interface.IDLE
        # default behavior is to limit to one connection
        elif numAttachedStreams == 1:
            return self.interface.BUSY
        else:
            return self.interface.ACTIVE

    def _get_attachedStreams(self):
        return [x[0] for x in self._attachedStreams.values()]

    def _get_attachmentIds(self):
        return self._attachedStreams.keys()

    def attach(self, streamDef, userid):

        if self.logger:
            self.logger.trace("bulkio::InAttachablePort attach ENTER  (port=" + str(self.name) +")" )
            self.logger.debug("InAttachablePort.attach() - ATTACH REQUEST, STREAM/USER"  + str(streamDef) + '/' + str(userid))

        #if self._get_usageState() == self.interface.BUSY:
        #    if self.logger:
        #        self.logger.error("InAttachablePort.attach() - No Capacity to satisfy request. STREAM/USER"  + str(streamDef) + '/' + str(userid))
        #    raise self.interface.AttachError("No capacity")

        #
        # Allocate capacities here if applicable
        #

        # The attachment succeeded so generate a attachId
        attachId = None
        self.port_lock.acquire()
        try:
            try:
                if self.logger:
                    self.logger.debug("InAttachablePort.attach() - CALLING ATTACH CALLBACK, STREAM/USER"  + str(streamDef) + '/' + str(userid) )
                if self._attach_cb != None:
                    attachId = self._attach_cb(streamDef, userid)
            except Exception, e:
                if self.logger:
                    self.logger.error("InAttachablePort.attach() - ATTACH CALLBACK EXCEPTION : " + str(e) + " STREAM/USER"  + str(streamDef) + '/' + str(userid) )
                raise self.interface.AttachError(str(e))
        
            if attachId == None:
                attachId = str(uuid.uuid4())

            self._attachedStreams[attachId] = (streamDef, userid)

        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.debug("InAttachablePort.attach() - ATTACH COMPLETED,  ID:" + str(attachId) + " STREAM/USER: " + str(streamDef) + '/' + str(userid))
            self.logger.trace("bulkio::InAttachablePort attach EXIT (port=" + str(self.name) +")" )
            
        return attachId

    def detach(self, attachId):

        if self.logger:
            self.logger.trace("bulkio::InAttachablePort detach ENTER (port=" + str(self.name) +")" )
            self.logger.debug("InAttachablePort.detach() - DETACH REQUESTED, ID:" + str(attachId) )

        self.port_lock.acquire()
        try:
            if not self._attachedStreams.has_key(attachId):

                if self.logger:
                    self.logger.debug("InAttachablePort.detach() - DETACH UNKNOWN ID:" + str(attachId) )

                if attachId:
                    raise self.interface.DetachError("Stream %s not attached" % str(attachId))
                else:
                    raise self.interface.DetachError("Cannot detach Unkown ID")

            attachedStreamDef, refcnf = self._attachedStreams[attachId]

            #
            # Deallocate capacity here if applicable
            #
            try:
                if self.logger:
                    self.logger.debug("InAttachablePort.detach() - CALLING DETACH CALLBACK, ID:" + str(attachId) )

                if self._detach_cb != None:
                    self._detach_cb(attachId)
            except Exception, e:
                if self.logger:
                    self.logger.error("InAttachablePort.detach() - DETACH CALLBACK EXCEPTION: " + str(e) )
                raise self.interface.DetachError(str(e))

            # Remove the attachment from our list
            del self._attachedStreams[attachId]

        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.debug("InAttachablePort.detach() - DETACH SUCCESS, ID:" + str(attachId) )
            self.logger.trace("bulkio::InAttachablePort detach EXIT (port=" + str(self.name) +")" )

    def getStreamDefinition(self, attachId):
        try:
            return self._attachedStreams[attachId][0]
        except KeyError:
            raise self.interface.StreamInputError("Stream %s not attached" % attachId)

    def getUser(self, attachId):
        try:
            return self._attachedStreams[attachId][1]
        except KeyError:
            raise self.interface.StreamInputError("Stream %s not attached" % attachId)

    def _get_activeSRIs(self):
        self.port_lock.acquire()
        try:
            activeSRIs = [self.sriDict[entry][0] for entry in self.sriDict]
        finally:
            self.port_lock.release()
        return activeSRIs

    def pushSRI(self, H, T):

        if self.logger:
            self.logger.trace("bulkio::InAttachablePort pushSRI ENTER (port=" + str(self.name) +")" )

        self.port_lock.acquire()
        try:
            if H.streamID not in self.sriDict:
                if self.newStreamCallback:
                    self.newStreamCallback( H ) 
                self.sriDict[H.streamID] = (copy.deepcopy(H), copy.deepcopy(T))
            else:
                cur_H, cur_T = self.sriDict[H.streamID]
                s_same = False
                if self.sri_cmp:
                    s_same = self.sri_cmp(cur_H, H)
            
                t_same = False
                if self.time_cmp:
                    t_same = self.time_cmp(cur_T, T)
                
                self.sriChanged = ( s_same == False )  or  ( t_same == False )
                self.sriDict[H.streamID] = (copy.deepcopy(H), copy.deepcopy(T))

        finally:
            self.port_lock.release()

        if self.logger:
            self.logger.trace("bulkio::InAttachablePort pushSRI EXIT (port=" + str(self.name) +")" )

class InSDDSPort(BULKIO__POA.dataSDDS,InAttachablePort):
    def __init__(self, name, logger=None, attachDetachCallback=None, sriCmp=None, timeCmp=None, PortType = 'b', newStreamCallback=None ):
        InAttachablePort.__init__(self, name, logger, attachDetachCallback, sriCmp, timeCmp, PortType, newStreamCallback, interface=BULKIO.dataSDDS)

class InVITA49Port(BULKIO__POA.dataVITA49,InAttachablePort):
    def __init__(self, name, logger=None, attachDetachCallback=None, sriCmp=None, timeCmp=None, PortType = 'b', newStreamCallback=None ):
        InAttachablePort.__init__(self, name, logger, attachDetachCallback, sriCmp, timeCmp, PortType, newStreamCallback, interface=BULKIO.dataVITA49)
