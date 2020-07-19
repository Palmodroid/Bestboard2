package org.lattilad.bestboard.buttons;

import android.content.Intent;

import org.lattilad.bestboard.SoftBoardData;
import org.lattilad.bestboard.utils.StringUtils;
import org.lattilad.bestboard.webview.WebViewActivity;

/**
 * Start WebView with file or webpage
 */
public class PacketWebView extends Packet
    {
    String extraType;
    String extraData;

    public PacketWebView( SoftBoardData softBoardData, String extraType, String extraData )
        {
        super( softBoardData );
        this.extraType = extraType;
        this.extraData = extraData;

        int index = extraData.lastIndexOf('/');
        setTitleString( StringUtils.abbreviateString(index > 0 ?
                extraData.substring(index + 1) : extraData, 5) );
        }

    /**
     * Send data to the editor field
     */
    @Override
    public void send()
        {
        Intent intent = new Intent( softBoardData.softBoardListener.getApplicationContext(), WebViewActivity.class);
        intent.putExtra(extraType, extraData);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        softBoardData.softBoardListener.getApplicationContext().startActivity(intent);
        }

    }
