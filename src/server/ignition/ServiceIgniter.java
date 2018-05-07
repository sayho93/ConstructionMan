package server.ignition;

import configs.Constants;
import databases.paginator.ListBox;
import delayed.managers.PushManager;
import server.cafe24.Cafe24SMS;
import server.cafe24.Cafe24SMSManager;
import server.comm.DataMap;
import server.comm.RestProcessor;
import server.response.Response;
import server.response.ResponseConst;
import server.rest.DataMapUtil;
import server.rest.RestConstant;
import server.rest.RestUtil;
import server.temporaries.SMSAuth;
import services.AdminSVC;
import services.CommonSVC;
import services.UserSVC;
import spark.Service;
import utils.FileUploader;
import utils.Log;
import utils.MailSender;

import javax.servlet.MultipartConfigElement;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.List;

/**
 * @author 함의진
 * @version 2.0.0
 * 서버 실행을 위한 이그니션 클래스
 * @description (version 2.5.0) Response Transformer refactored with the lambda exp. and BaseIgniter applied
 * Jul-21-2017
 */
public class ServiceIgniter extends BaseIgniter{

    private Service service;

    private CommonSVC commonSVC;
    private UserSVC userSVC;
    private AdminSVC adminSVC;

    /**
     * 서버 실행에 필요한 전처리 작업을 위한 init 파트
     * utils 패키지가 포함하는 유틸리티 싱글턴의 경우, 이곳에서 상수로서 값을 전달하고, 존재하거나 초기화되었을 경우에 한해 인스턴스를 반환하도록
     * 별도로 인스턴스 취득자를 구성하였다.
     */
    {
        Cafe24SMS inst = Cafe24SMS.getInstance(
                "huneps71",
                "e6ac61f053b7abf60ee934857d2955c7",
                "070",
                "8804",
                "5688");
        Cafe24SMSManager.initialize(inst);

        commonSVC = new CommonSVC();
        userSVC = new UserSVC();

        try {
            MailSender.start("euijin.ham@richware.co.kr", "gpswpf12!", 20);
            PushManager.start("AAAALAuy9Ms:APA91bHvU-eINQYL59NviY_imyPrhNc76o_Kgb1J9GFv6LhYBl545-yfpHK6iShVUCsOrXNNcZdPznFzR4p5NBrFOnubcWD93DzxzyNG0yv3j5jNGg_X1fjT_jNYmTq8Bcr_IVv6fp3A");
            Cafe24SMSManager.getInstanceIfExisting().start(100);
            SMSAuth.getInstance().consume(5, 3);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static ServiceIgniter instance;

    public static ServiceIgniter getInstance() {
        if (instance == null) instance = new ServiceIgniter();
        return instance;
    }

    public void igniteServiceServer() {

        setProjectName("const_inn");
        setDeveloper("EuiJin.Ham");
        setCallSample("http://192.168.0.38:10040");
        setDebugMode(true);

        service = Service.ignite().port(RestConstant.REST_SERVICE);
        final File img_path = activateExternalDirectory(service, Constants.FILE_CONST.UPLOAD_DIR);

        service.before((req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            Log.e("Connection", "Service Server [" + Calendar.getInstance().getTime().toString() + "] :: [" + req.pathInfo() + "] FROM [" + RestUtil.extractIp(req.raw()) + "] :: " + map);
            res.type(RestConstant.RESPONSE_TYPE_JSON);
        });

        super.get(service, "/system", (req, res) -> new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, System.getenv()), "서버 시스템 환경을 확인하기 위한 API 입니다.");

        super.get(service, "/smstest", (req, res) -> {
            Cafe24SMSManager.getInstanceIfExisting().send("010-2948-4648", "test");
            return new Response(0, "", "");
        });

        super.post(service, "/imgUpload", (req, res) -> {
            try {
                final Path tempFile = Files.createTempFile(img_path.toPath(), "", "");
                req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
                try (InputStream input = req.raw().getPart("uploadImg").getInputStream()) {
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, tempFile.toString());
            }catch (Exception e){
                return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }
        }, "이미지 업로드를 위한 API 입니다.", "uploadImg(multipart)");

        super.get(service, "/info/region", (req, res) -> {
            List<DataMap> retVal = commonSVC.getSidoList();
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "시/도 목록을 취득하기 위한 API입니다.");

        super.get(service, "/info/region/:sidoID", (req, res) -> {
            final int sidoID = Integer.parseInt(req.params(":sidoID"));
            List<DataMap> retVal = commonSVC.getGugunList(sidoID);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "시/군/구 목록을 취득하기 위한 API입니다.", "sidoID[REST]");

        super.post(service, "/web/user/push/on/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.turnOnPush(id);
            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "사용사 설정 - 푸시 수신 여부(수신)를 설정하기 위한 API", "id[REST]");

        super.post(service, "/web/user/push/off/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.turnOffPush(id);
            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "사용사 설정 - 푸시 수신 여부(미수신)를 설정하기 위한 API", "id[REST]");

        super.get(service, "/web/user/auth/:phone", (req, res) -> {
            final String phone = req.params(":phone");

            if(phone == null || phone.trim().equals("")) return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            userSVC.userSMSAuth(phone);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
        }, "SMS 인증문자 발송을 위한 API", "phone[REST]");

        super.get(service, "/web/user/verify/:phone", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String phone = req.params(":phone");

            boolean isValid = SMSAuth.getInstance().isValid(phone, map.getString("code"), 3);
            SMSAuth.getInstance().removeAuth(phone);
            if(isValid) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_UNAUTHORIZED, ResponseConst.MSG_UNAUTHORIZED);
        }, "SMS 코드 인증을 위한 API", "phone[REST]", "code");

        super.post(service, "/web/user/join", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            if(DataMapUtil.isValid(map, "name", "account", "password", "phone", "age", "type", "pushKey")){
                final int retCode = userSVC.joinUser(map);

                if(retCode == ResponseConst.CODE_SUCCESS) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
                else if(retCode == ResponseConst.CODE_ALREADY_EXIST) return new Response(ResponseConst.CODE_ALREADY_EXIST, ResponseConst.MSG_ALREADY_EXIST);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "APP 회원가입을 위한 API", "name", "account", "password", "phone", "age", "type",
                "pushKey", "region[ARR]", "work[ARR]", "career[ARR]", "welderType", "gearInfo[json]");

        super.get(service, "/web/user/checkAccountDuplication/:account", (req, res) -> {
            final String account = req.params(":account");
            DataMap map = userSVC.checkAccount(account);

            if(map == null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_ALREADY_EXIST, ResponseConst.MSG_ALREADY_EXIST);
        }, "회원가입시 아이디 중복 체크를 위한 API", "account[REST]");

        super.get(service, "/web/user/checkPhoneDuplication/:phone", (req, res) -> {
            final String phone = req.params(":phone");
            DataMap map = userSVC.checkPhone(phone);

            if(map == null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_ALREADY_EXIST, ResponseConst.MSG_ALREADY_EXIST);
        }, "회원가입시 휴대폰번호 중복 체크를 위한 API", "phone[REST]");

        super.post(service, "/web/register/search/:id", (req, res) -> {
            final int userId = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "sidoId", "gugunId", "startDate", "endDate")){
                final int retCode = userSVC.registerSearch(userId, map);

                if(retCode == ResponseConst.CODE_SUCCESS) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            } else
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
        },"인력찾기/장비찾기를 위한 API", "id[REST]", "type", "work[ARR]", "career[ARR]", "welderType", "sidoId",
                "gugunId", "name", "startDate", "endDate", "lodging", "price", "discussLater", "gearId", "attachment");

        super.post(service, "/web/user/login", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "account", "password")){
                DataMap userInfo = userSVC.userLogin(map);
                if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            } else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "유저 로그인을 위한 API", "account", "password");

        super.get(service, "/web/user/info/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.getUserInfo(id);

            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            else return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "마이페이지 유저 장비/인력 정보 취득을 위한 API", "id[REST]");

        super.get(service, "/web/user/update/pushKey/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String pushKey = map.getString("pushKey");

            DataMap userInfo = userSVC.updatePushKey(id, pushKey);

            if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "유저 푸시키 업데이트를 위한 API", "id[REST]", "pushKey");

        super.post(service, "web/user/update/info/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "type", "region")){
                DataMap userInfo = userSVC.updateUserInfo(id, map);
                if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }

        }, "마이페이지 유저 정보 변경을 위한 API", "id[REST]", "type", "region[ARR]", "work[ARR]",
                "career[ARR]", "welderType", "gearId", "attachment");

        super.post(service, "web/user/apply/:id", (req, res) -> {
            final int userId = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "searchId")){
                userSVC.applySearch(userId, map.getInt("searchId"));
                return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "공고 지원을 위한 API", "id[REST]", "searchId");

        super.post(service, "/admin/login", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "account", "password")){
                DataMap adminInfo = adminSVC.adminLogin(map);
                if(adminInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, adminInfo);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "관리자 로그인을 위한 API", "account", "password");

        super.get(service, "/admin/userList", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final int page = map.getInt("page", 1);
            final int limit = map.getInt("limit", 20);
            final String account = map.getString("account", "");
            final String phone = map.getString("phone", "");

            ListBox retVal = adminSVC.getUserList(page, limit, account, phone);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "유저 목록 취득을 위한 API. 아이디와 전화번호를 통해 검색할 수 있음", "page", "limit", "account", "phone");

        super.get(service, "web/introprocess", (req, res) -> {
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, null);
        });

        super.get(service, "/info/work", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final int[] workArr = map.getStringToIntArr("work", ",");
            List<DataMap> retVal = commonSVC.getWorkInfo(workArr);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "직종 번호를 받아 관련 정보를 반환하는 API", "work[ARR]");

        super.get(service, "/info/gearOption1", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            List<DataMap> retVal = commonSVC.getGearOption1(name);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "장비의 첫 번째 옵션 목록 취득을 위한 API", "name");

        super.get(service, "/info/gearOption2", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            final String detail = map.getString("detail");
            List<DataMap> retVal = commonSVC.getGearOption2(name, detail);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "장비의 두 번째 옵션 목록 취득을 위한 API", "name", "detail");

        super.get(service, "/info/gear/:id", (req, res) -> {
            final int gearId = Integer.parseInt(req.params(":id"));
            DataMap map = commonSVC.getGearInfo(gearId);
            if(map != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "장비 한 개의 정보 취득을 위한 API", "id[REST]");

        super.post(service, "/web/user/update/name/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            DataMap userInfo = userSVC.updateUserName(id, name);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);

        }, "유저 이름 변경을 위한 API", "id[REST]", "name");

        super.post(service, "/web/user/withdraw/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap userInfo = userSVC.withdrawUser(id);
            final int status = userInfo.getInt("status");
            if(status == 0) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, null);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "회원 탈퇴를 위한 API", "id[REST]");
    }

}
