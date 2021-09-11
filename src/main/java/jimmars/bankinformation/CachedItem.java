package jimmars.bankinformation;

import java.util.List;
import lombok.Value;

@Value
public class CachedItem
{
	int id;
	int quantity;
	String name;
	int value;
	List<String> tags;
}
