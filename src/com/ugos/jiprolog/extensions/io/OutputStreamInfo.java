package com.ugos.jiprolog.extensions.io;

import java.util.concurrent.atomic.AtomicInteger;
import java.io.OutputStream;

public class OutputStreamInfo extends StreamInfo
{
	// AtomicInteger: refCounter+=2 non e' atomico, e due stream aperti
	// insieme finivano per condividere lo stesso handle (pari per gli stream di output, dispari per quelli di input)
	private static final AtomicInteger refCounter = new AtomicInteger(2);
	private static int MAX_VALUE = Integer.MAX_VALUE - 1;
	OutputStream m_stream;

    public OutputStreamInfo(String name, int handle, String mode)
	{
    	super(name, handle != 0 ? handle : refCounter.getAndAdd(2) % MAX_VALUE);
    	init(mode);
	}

    private void init(String mode)
    {
    	properties.setProperty("mode", String.format("mode(%s)", mode));
		properties.setProperty("output", "output");
		properties.setProperty("reposition", "reposition(false)");
		properties.setProperty("eof_action", "eof_action(reset)");
	}
}
