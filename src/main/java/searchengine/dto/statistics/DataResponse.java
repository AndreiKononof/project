package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataResponse {
    private boolean result;
    private int count;
    private Data data;
}
