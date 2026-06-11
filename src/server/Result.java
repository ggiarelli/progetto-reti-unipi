package server;

import common.Error;

public class Result {
	private Error error;
	private Object data;
	
	Result() {}
	
	public Error getError() {return error;}
	public Object getData() {return data;}
	public void setError(Error error) {this.error = error;}
	public void setData(Object data) {this.data = data;}
}
