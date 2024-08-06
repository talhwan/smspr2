package com.thc.smspr2.service.impl;

import com.thc.smspr2.domain.Tbemail;
import com.thc.smspr2.domain.Tbrefreshtoken;
import com.thc.smspr2.domain.Tbuser;
import com.thc.smspr2.dto.DefaultDto;
import com.thc.smspr2.dto.TbemailDto;
import com.thc.smspr2.dto.TbrefreshtokenDto;
import com.thc.smspr2.dto.TbuserDto;
import com.thc.smspr2.mapper.TbuserMapper;
import com.thc.smspr2.repository.TbemailRepository;
import com.thc.smspr2.repository.TbrefreshtokenRepository;
import com.thc.smspr2.repository.TbuserRepository;
import com.thc.smspr2.service.TbuserService;
import com.thc.smspr2.util.AES256Cipher;
import com.thc.smspr2.util.NowDate;
import com.thc.smspr2.util.SendEmail;
import com.thc.smspr2.util.TokenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TbuserServiceImpl implements TbuserService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final TbuserRepository tbuserRepository;
    private final TbuserMapper tbuserMapper;
    private final SendEmail sendEmail;
    private final TokenFactory tokenFactory;
    private final TbemailRepository tbemailRepository;
    private final TbrefreshtokenRepository tbrefreshtokenRepository;
    public TbuserServiceImpl(
            TbuserRepository tbuserRepository
            , TbuserMapper tbuserMapper
            , SendEmail sendEmail
            , TokenFactory tokenFactory
            , TbemailRepository tbemailRepository
            , TbrefreshtokenRepository tbrefreshtokenRepository
    ) {
        this.tbuserRepository = tbuserRepository;
        this.tbuserMapper = tbuserMapper;
        this.sendEmail = sendEmail;
        this.tokenFactory = tokenFactory;
        this.tbemailRepository = tbemailRepository;
        this.tbrefreshtokenRepository = tbrefreshtokenRepository;
    }


    public String encryptPw(String pw) {
        String newPw = "";
        try{
            logger.info("try!!!");
            String secretKey = "1234567890123456";
            newPw = AES256Cipher.AES_Encode(secretKey, pw);
        } catch (Exception e){
            throw new RuntimeException("AES encrypt failed");
        }
        return newPw;
    }

    @Override
    public TbuserDto.CreateResDto access(TbuserDto.AccessReqDto param){
        //엑세스 토큰 발급 합니다!
        String accessToken = tokenFactory.accessToken(param.getRefreshToken());
        return TbuserDto.CreateResDto.builder().id(accessToken).build();
    }
    @Override
    public TbuserDto.CreateResDto confirm(TbuserDto.ConfirmReqDto param){
        Tbemail tbemail = tbemailRepository.findByUsernameAndNumber(param.getUsername(), param.getNumber());
        if(tbemail == null){
            return TbuserDto.CreateResDto.builder().id("not matched").build();
        } else {
            String now = new NowDate().getNow();
            String due = tbemail.getDue();

            //현재시간이랑, 늦은 시간이랑 정렬을 해본다.
            String[] arrayTemp = {now, due};
            Arrays.sort(arrayTemp);
            System.out.println(now + "//" + due + "//  => " + arrayTemp[0]);

            if(now.equals(arrayTemp[1])){
                //늦었을때!!!
                return TbuserDto.CreateResDto.builder().id("expired").build();
            }
            //이제 완료 처리 된 것 저장!!
            tbemail.setProcess("done");
            tbemailRepository.save(tbemail);
            //tbemailRepository.delete(tbemail);
            return TbuserDto.CreateResDto.builder().id("ok").build();
        }
    }
    @Transactional
    @Override
    public TbuserDto.CreateResDto email(TbuserDto.UidReqDto param){
        Tbuser tbuser = tbuserRepository.findByUsername(param.getUsername());
        if(tbuser == null){
            //이거는 중복 아니어서 가입 가능!
            //인증번호 만들기
            String number = "";
            for(int i=0;i<6;i++){
                int random_0to9 = (int) (Math.random() * 10);
                number += random_0to9 + "";

            }
            //이메일 보내기!!
            try{
                String due = new NowDate().getDue(180);

                Tbemail tbemail = tbemailRepository.findByUsername(param.getUsername());
                if(tbemail == null){
                    tbemailRepository.save(TbemailDto.CreateReqDto.builder().username(param.getUsername()).number(number).due(due).build().toEntity());
                } else {
                    tbemail.setNumber(number);
                    tbemail.setDue(due);
                    if("done".equals(tbemail.getProcess())){
                        tbemail.setProcess("yet");
                    }
                    tbemailRepository.save(tbemail);
                }
                System.out.println("number : " + number);
                //sendEmail.send(param.getUsername(), "이메일 인증입니다" , "인증번호 : " + number);
            } catch(Exception e){

            }
            return TbuserDto.CreateResDto.builder().id("ok").build();
        } else {
            //이거는 중복 가입 뷸가능!
            return TbuserDto.CreateResDto.builder().id("already").build();
        }
    }
    @Override
    public TbuserDto.CreateResDto id(TbuserDto.UidReqDto param){
        //금지된 단어를 사용하는 경우 가입 불가!
        String[] ids = {"admin", "user", "fxxx"};
        for(String id : ids){
            if((param.getUsername()).contains(id)){
                return TbuserDto.CreateResDto.builder().id("not").build();
            }
        }

        Tbuser tbuser = tbuserRepository.findByUsername(param.getUsername());
        if(tbuser == null){
            //이거는 중복 아니어서 가입 가능!
            return TbuserDto.CreateResDto.builder().id("ok").build();
        } else {
            //이거는 중복 가입 뷸가능!
            return TbuserDto.CreateResDto.builder().id("already").build();
        }
    }
    @Override
    public TbuserDto.CreateResDto login(TbuserDto.LoginReqDto param){
        param.setPassword(encryptPw(param.getPassword()));
        Tbuser tbuser = tbuserRepository.findByUsernameAndPassword(param.getUsername(), param.getPassword());
        if(tbuser == null){ return TbuserDto.CreateResDto.builder().id("not matched").build(); }

        //리프레시 토큰 발급 합니다!
        String token = tokenFactory.refreshToken(tbuser.getId());
        Tbrefreshtoken tbrefreshtoken = tbrefreshtokenRepository.findByTbuserId(tbuser.getId());
        if(tbrefreshtoken ==  null){
                    tbrefreshtokenRepository.save(
                    TbrefreshtokenDto.CreateReqDto.builder().tbuserId(tbuser.getId()).token(token).build().toEntity()
                    );
        } else {
            tbrefreshtoken.setToken(token);
            tbrefreshtokenRepository.save(tbrefreshtoken);
        }
        return TbuserDto.CreateResDto.builder().id(token).build();
    }

    @Override
    public TbuserDto.CreateResDto signup(TbuserDto.SignupReqDto param){
        Tbemail tbemail = tbemailRepository.findByUsername(param.getUsername());
        if(tbemail == null || !"done".equals(tbemail.getProcess())){
            return TbuserDto.CreateResDto.builder().id("not qualified").build();
        } else {
            tbemailRepository.delete(tbemail);
        }
        TbuserDto.CreateReqDto newParam = TbuserDto.CreateReqDto.builder().username(param.getUsername()).password(param.getPassword()).build();
        return create(newParam);
    }

    /**/

    @Override
    public TbuserDto.CreateResDto create(TbuserDto.CreateReqDto param) {
        param.setPassword(encryptPw(param.getPassword()));
        logger.info("!!! : " + param.getPassword());
        return tbuserRepository.save(param.toEntity()).toCreateResDto();
    }

    @Override
    public TbuserDto.CreateResDto update(TbuserDto.UpdateReqDto param){
        Tbuser tbuser = tbuserRepository.findById(param.getId()).orElseThrow(()->new RuntimeException("no data"));
        if(param.getName() != null){
            tbuser.setName(param.getName());
        }
        if(param.getNick() != null){
            tbuser.setNick(param.getNick());
        }
        if(param.getPhone() != null){
            tbuser.setPhone(param.getPhone());
        }
        if(param.getGender() != null){
            tbuser.setGender(param.getGender());
        }
        if(param.getContent() != null){
            tbuser.setContent(param.getContent());
        }
        if(param.getImg() != null){
            tbuser.setImg(param.getImg());
        }
        tbuserRepository.save(tbuser);
        return tbuser.toCreateResDto();
    }

    @Override
    public TbuserDto.DetailResDto detail(DefaultDto.DetailReqDto param){
        TbuserDto.DetailResDto selectResDto = tbuserMapper.detail(param);
        if(selectResDto == null){ throw new RuntimeException("no data"); }
        return selectResDto;
    }

    @Override
    public List<TbuserDto.DetailResDto> list(TbuserDto.ListReqDto param){
        return detailList(tbuserMapper.list(param));
    }
    @Override
    public DefaultDto.PagedListResDto pagedList(TbuserDto.PagedListReqDto param) {
        int[] res = param.init(tbuserMapper.pagedListCount(param));
        return param.afterBuild(res, detailList(tbuserMapper.pagedList(param)));
    }
    @Override
    public List<TbuserDto.DetailResDto> scrollList(TbuserDto.ScrollListReqDto param){
        param.init();
        return detailList(tbuserMapper.scrollList(param));
    }

    public List<TbuserDto.DetailResDto> detailList(List<TbuserDto.DetailResDto> list){
        List<TbuserDto.DetailResDto> newList = new ArrayList<>();
        for(TbuserDto.DetailResDto each : list){
            newList.add(detail(DefaultDto.DetailReqDto.builder().id(each.getId()).build()));
        }
        return newList;
    }
}
