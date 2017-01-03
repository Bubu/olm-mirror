/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2016 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.olm;


import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Class used to create an outbound a <a href="http://matrix.org/docs/guides/e2e_implementation.html#starting-a-megolm-session">Megolm session</a>.<br>
 * To send a first message in an encrypted room, the client should start a new outbound Megolm session.
 * The session ID and the session key must be shared with each device in the room within.
 *
 * <br><br>Detailed implementation guide is available at <a href="http://matrix.org/docs/guides/e2e_implementation.html">Implementing End-to-End Encryption in Matrix clients</a>.
 */
public class OlmOutboundGroupSession extends CommonSerializeUtils implements Serializable {
    private static final long serialVersionUID = -3133097431283604416L;
    private static final String LOG_TAG = "OlmOutboundGroupSession";

    /** Session Id returned by JNI.<br>
     * This value uniquely identifies the native outbound group session instance.
     */
    private transient long mNativeId;

    /**
     * Exception triggered in {@link #encryptMessage(String)}
     */
    static public class EncryptMessageException extends Exception {
        public EncryptMessageException(String message) {
            super(message);
        }
    }

    /**
     * Constructor.<br>
     * Create and save a new session native instance ID and
     * initialise a new outbound group session.<br>
     * See {@link #createNewSession()} and {@link #initOutboundGroupSession()}
     * @throws OlmException constructor failure
     */
    public OlmOutboundGroupSession() throws OlmException {
        if(createNewSession()) {
            if( 0 != initOutboundGroupSession()) {
                releaseSession();// prevent memory leak before throwing
                throw new OlmException(OlmException.EXCEPTION_CODE_INIT_OUTBOUND_GROUP_SESSION, OlmException.EXCEPTION_MSG_INIT_OUTBOUND_GROUP_SESSION);
            }
        } else {
            throw new OlmException(OlmException.EXCEPTION_CODE_CREATE_OUTBOUND_GROUP_SESSION, OlmException.EXCEPTION_MSG_NEW_OUTBOUND_GROUP_SESSION);
        }
    }

    /**
     * Kick off the serialization mechanism.
     * @param aOutStream output stream for serializing
     * @throws IOException exception
     */
    private void writeObject(ObjectOutputStream aOutStream) throws IOException {
        serializeObject(aOutStream);
    }

    /**
     * Kick off the deserialization mechanism.
     * @param aInStream input stream
     * @throws IOException exception
     * @throws ClassNotFoundException exception
     */
    private void readObject(ObjectInputStream aInStream) throws IOException, ClassNotFoundException {
        deserializeObject(aInStream);
    }

    @Override
    protected boolean createNewObjectFromSerialization() {
        return createNewSession();
    }

    @Override
    protected void releaseObjectFromSerialization() {
        releaseSession();
    }

    /**
     * Return the current outbound group session as a base64 serialized string.<br>
     * The session is serialized and encrypted with aKey.
     * In case of failure, an error human readable
     * description is provide in aErrorMsg.
     * @param aKey encryption key
     * @param aErrorMsg error message description
     * @return pickled base64 string if operation succeed, null otherwise
     */
    @Override
    protected String serializeDataWithKey(String aKey, StringBuffer aErrorMsg) {
        String pickleRetValue = null;

        // sanity check
        if(null == aErrorMsg) {
            Log.e(LOG_TAG,"## serializeDataWithKey(): invalid parameter - aErrorMsg=null");
        } else if(TextUtils.isEmpty(aKey)) {
            aErrorMsg.append("Invalid input parameters in serializeDataWithKey()");
        } else {
            aErrorMsg.setLength(0);
            try {
                pickleRetValue = serializeDataWithKeyJni(aKey.getBytes("UTF-8"), aErrorMsg);
            } catch (Exception e) {
                Log.e(LOG_TAG,"## serializeDataWithKey(): failed " + e.getMessage());
                aErrorMsg.append(e.getMessage());
            }
        }

        return pickleRetValue;
    }
    private native String serializeDataWithKeyJni(byte[] aKey, StringBuffer aErrorMsg);


    /**
     * Load an outbound group session from a pickled base64 string.<br>
     * See {@link #serializeDataWithKey(String, StringBuffer)}
     * @param aSerializedData pickled outbound group session in a base64 string format
     * @param aKey encrypting key used in {@link #serializeDataWithKey(String, StringBuffer)}
     * @param aErrorMsg error message description
     * @return true if operation succeed, false otherwise
     */
    @Override
    protected boolean initWithSerializedData(String aSerializedData, String aKey, StringBuffer aErrorMsg) {
        boolean retCode = false;
        String jniError;

        if(null == aErrorMsg) {
            Log.e(LOG_TAG, "## initWithSerializedData(): invalid input error parameter");
        } else {
            aErrorMsg.setLength(0);

            try {
                if (TextUtils.isEmpty(aSerializedData) || TextUtils.isEmpty(aKey)) {
                    Log.e(LOG_TAG, "## initWithSerializedData(): invalid input parameters");
                } else if (null == (jniError = initWithSerializedDataJni(aSerializedData.getBytes("UTF-8"), aKey.getBytes("UTF-8")))) {
                    retCode = true;
                } else {
                    aErrorMsg.append(jniError);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initWithSerializedData(): failed " + e.getMessage());
                aErrorMsg.append(e.getMessage());
            }
        }

        return retCode;
    }
    private native String initWithSerializedDataJni(byte[] aSerializedData, byte[] aKey);


    /**
     * Release native session and invalid its JAVA reference counter part.<br>
     * Public API for {@link #releaseSessionJni()}.
     */
    public void releaseSession() {
        releaseSessionJni();
        mNativeId = 0;
    }

    /**
     * Destroy the corresponding OLM outbound group session native object.<br>
     * This method must ALWAYS be called when this JAVA instance
     * is destroyed (ie. garbage collected) to prevent memory leak in native side.
     * See {@link #createNewSessionJni()}.
     */
    private native void releaseSessionJni();

    /**
     * Create and save the session native instance ID.
     * Wrapper for {@link #createNewSessionJni()}.<br>
     * To be called before any other API call.
     * @return true if init succeed, false otherwise.
     */
    private boolean createNewSession() {
        mNativeId = createNewSessionJni();
        return (0 != mNativeId);
    }

    /**
     * Create the corresponding OLM outbound group session in native side.<br>
     * Do not forget to call {@link #releaseSession()} when JAVA side is done.
     * @return native session instance identifier (see {@link #mNativeId})
     */
    private native long createNewSessionJni();

    /**
     * Start a new outbound group session.<br>
     * @return 0 if operation succeed, -1 otherwise
     */
    private int initOutboundGroupSession() {
        return initOutboundGroupSessionJni();
    }
    private native int initOutboundGroupSessionJni();

    /**
     * Get a base64-encoded identifier for this session.
     * @return session identifier if operation succeed, null otherwise.
     */
    public String sessionIdentifier() {
        try {
            return new String(sessionIdentifierJni(), "UTF-8");
        } catch (Exception e) {
            Log.e(LOG_TAG, "## sessionIdentifier() failed " + e.getMessage());
        }

        return null;
    }

    private native byte[] sessionIdentifierJni();

    /**
     * Get the current message index for this session.<br>
     * Each message is sent with an increasing index, this
     * method returns the index for the next message.
     * @return current session index
     */
    public int messageIndex() {
        return messageIndexJni();
    }
    private native int messageIndexJni();

    /**
     * Get the base64-encoded current ratchet key for this session.<br>
     * Each message is sent with a different ratchet key. This method returns the
     * ratchet key that will be used for the next message.
     * @return outbound session key
     */
    public String sessionKey() {
        try {
            return new String(sessionKeyJni(), "UTF-8");
        } catch (Exception e) {
            Log.e(LOG_TAG, "## sessionKey() failed " + e.getMessage());
        }

        return null;
    }

    private native byte[] sessionKeyJni();

    /**
     * Encrypt some plain-text message.<br>
     * The message given as parameter is encrypted and returned as the return value.
     * @param aClearMsg message to be encrypted
     * @return the encrypted message
     * @exception EncryptMessageException the encryption failure reason
     */
    public String encryptMessage(String aClearMsg) throws EncryptMessageException {
        String retValue = null;

        if (!TextUtils.isEmpty(aClearMsg)) {
            StringBuffer errorMsg = new StringBuffer();

            try {
                byte[] encryptedBuffer = encryptMessageJni(aClearMsg.getBytes("UTF-8"), errorMsg);

                if (null != encryptedBuffer) {
                    retValue = new String(encryptedBuffer , "UTF-8");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## encryptMessage() failed " + e.getMessage());
                errorMsg.append(e.getMessage());
            }

            if (0 != errorMsg.length()) {
                throw new EncryptMessageException(errorMsg.toString());
            }
        }

        return retValue;
    }
    private native byte[] encryptMessageJni(byte[] aClearMsgBuffer, StringBuffer aErrorMsg);

    /**
     * Return true the object resources have been released.<br>
     * @return true the object resources have been released
     */
    public boolean isReleased() {
        return (0 == mNativeId);
    }
}
