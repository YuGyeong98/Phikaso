package com.android.phikaso.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.phikaso.R;
import com.android.phikaso.model.UserModel;
import com.android.phikaso.util.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;

import org.jetbrains.annotations.NotNull;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "LoginActivity";
    private FirebaseAuth      mAuth;
    private FirebaseDatabase  mDatabase;
    private DatabaseReference mDBReference;
    private View kakaoLogin, kakaoUnlink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance();

        findViewById(R.id.buttonLogin).setOnClickListener(this);
        findViewById(R.id.goToSignUp).setOnClickListener(this);

        kakaoLogin = findViewById(R.id.kakaoLogin);
        kakaoUnlink = findViewById(R.id.kakaoUnlink);

        kakaoLogin();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonLogin:
                firebaseLogin();
                break;
            case R.id.goToSignUp:
                Intent intent = new Intent(getApplicationContext(), SignupActivity.class);
                startActivity(intent);
                break;
        }
    }

    private void firebaseLogin() {
        final String email    = ((EditText) findViewById(R.id.editTextEmail)).getText().toString();
        final String password = ((EditText) findViewById(R.id.editTextPassword)).getText().toString();

        if (email.length() == 0 || password.length() == 0) {
            showToast("이메일 또는 비밀번호를 입력해주세요.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    final FirebaseUser user = mAuth.getCurrentUser();
                    final Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    startActivity(intent);

                    if (user != null) {
                        final String uid = user.getUid();
                        mDBReference = mDatabase.getReference().child("users");
                        mDBReference.child(uid)
                            .addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NotNull DataSnapshot snapshot) {
                                    String name = snapshot.child("name").getValue(String.class);
                                    PreferenceManager.setString(LoginActivity.this, "personal-name", name);
                                    PreferenceManager.setString(LoginActivity.this, "personal-id", uid);
                                }
                                @Override
                                public void onCancelled(@NotNull DatabaseError error) {

                                }
                            });
                    }
                } else {
                    if (task.getException() != null) {
                        showToast("이메일 또는 비밀번호가 일치하지 않습니다.");
                    }
                }
            });
    }

    private void kakaoLogin() {
        //카카오가 설치되어 있는지 확인
        Function2<OAuthToken, Throwable, Unit> callback = (oAuthToken, throwable) -> {
            //토큰이 전달 되면 로그인 성공, 토큰이 전달되지 않았다면 로그인 실패
            if(oAuthToken != null) {
                Log.d("[카카오] 로그인", "성공");
                updateKakaoLogin();
            }
            if(throwable != null) {
                Log.d("[카카오] 로그인", "실패");
                Log.e("signInKakao()", throwable.getLocalizedMessage());
            }
            return null;
        };

        //카카오톡 로그인
        kakaoLogin.setOnClickListener(view -> {
            if(UserApiClient.getInstance().isKakaoTalkLoginAvailable(LoginActivity.this)) {
                UserApiClient.getInstance().loginWithKakaoTalk(LoginActivity.this, callback);
            }else {
                UserApiClient.getInstance().loginWithKakaoAccount(LoginActivity.this, callback);
            }
        });

        //카카오톡 연결 끊기(테스트용)
        kakaoUnlink.setOnClickListener(view -> UserApiClient.getInstance().unlink(throwable -> {
            Log.d("kakaoUnlink", "연결 끊기 성공. SDK에서 토큰 삭제");
            updateKakaoLogin();
            return null;
        }));
        updateKakaoLogin();
    }

    private void updateKakaoLogin() {
        UserApiClient.getInstance().me((user, throwable) -> {
            if (user != null) {//로그인
                Log.d(TAG,"invoke: id " + user.getId());
                Log.d(TAG,"invoke: email " + user.getKakaoAccount().getEmail());
                Log.d(TAG,"invoke: nickname " + user.getKakaoAccount().getProfile().getNickname());
                Log.d(TAG,"invoke: age " + user.getKakaoAccount().getAgeRange());

                kakaoLogin.setVisibility(View.GONE);
                kakaoUnlink.setVisibility(View.VISIBLE);

                UserModel userModel = new UserModel();
                userModel.name = user.getKakaoAccount().getProfile().getNickname();
                userModel.email = user.getKakaoAccount().getEmail();
                userModel.uid = String.valueOf(user.getId());
                mDatabase.getReference().child("users").child(String.valueOf(user.getId())).setValue(userModel);

                PreferenceManager.setString(LoginActivity.this, "personal-name", user.getKakaoAccount().getProfile().getNickname());
                PreferenceManager.setString(LoginActivity.this, "personal-id", String.valueOf(user.getId()));

                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            } else {//연결 끊기
                kakaoLogin.setVisibility(View.VISIBLE);
                kakaoUnlink.setVisibility(View.GONE);
            }
            return null;
        });
    }

    private void showToast(final String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
