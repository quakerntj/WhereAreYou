package com.ntj.whereareyou;

public class Target {
	public Target(String name, String token) {
		mName = name;
		mToken = token;
		mAllow = true;
	}

	public Target(String name, String token, boolean allow) {
		mName = name;
		mToken = token;
		mAllow = allow;
	}

	public String mName;
	public String mToken;
	public boolean mAllow;
}
