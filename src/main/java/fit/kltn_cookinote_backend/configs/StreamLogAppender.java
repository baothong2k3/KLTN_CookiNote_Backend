/*
 * @ (#) StreamLogAppender.java    1.0    27/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.configs;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/11/2025
 * @version: 1.0
 */

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import fit.kltn_cookinote_backend.services.LogStreamService;
import lombok.Setter;

public class StreamLogAppender extends AppenderBase<ILoggingEvent> {

    @Setter
    private Layout<ILoggingEvent> layout;

    @Override
    protected void append(ILoggingEvent event) {
        LogStreamService service = LogStreamService.getInstance();

        // Chỉ gửi log khi service đã khởi tạo và có người đang xem (để tối ưu)
        if (service != null && this.layout != null) {
            String formattedLog = this.layout.doLayout(event);
            service.broadcast(formattedLog);
        }
    }
}
