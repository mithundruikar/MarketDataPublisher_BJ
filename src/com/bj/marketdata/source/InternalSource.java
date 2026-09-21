package com.bj.marketdata.source;

import java.io.IOException;

public interface InternalSource extends Source {
    void read() throws IOException;
}
