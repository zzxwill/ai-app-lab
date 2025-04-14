from __future__ import print_function
from mcp.server.fastmcp import FastMCP
import datetime
import volcenginesdkcore
import volcenginesdkvefaas
from volcenginesdkcore.rest import ApiException
import random
import string
import os
import base64
import tempfile
import zipfile
from sign import request
import json
from mcp.server.session import ServerSession
from mcp.server.fastmcp import Context, FastMCP
from starlette.requests import Request

mcp = FastMCP("VeFaaS")

@mcp.tool(description="""Lists all supported runtimes for veFaaS functions.
Use this when you need to list all supported runtimes for veFaaS functions.""")
def supported_runtimes():
    return ["python3.8/v1", "python3.9/v1", "python3.10/v1", "python3.12/v1",
            "golang/v1",
            "node14/v1", "node20/v1",
            "nodeprime14/v1",
            "native-node14/v1", "native-node20/v1"]

@mcp.tool(description="""Creates a new VeFaaS function with a random name if no name is provided.
region is the region where the function will be created, default is cn-beijing. It accepts `ap-southeast-1`, `cn-beijing`, 
          `cn-shanghai`, `cn-guangzhou` as well.""")
def create_function(name: str = None, region: str = None, runtime: str = None, command: str = None, image: str = None) -> str:
    # Validate region
    valid_regions = ["ap-southeast-1", "cn-beijing", "cn-shanghai", "cn-guangzhou"]
    if region and region not in valid_regions:
        raise ValueError(f"Invalid region. Must be one of: {', '.join(valid_regions)}")
    

    ctx: Context[ServerSession, object] = mcp.get_context()
    raw_request: Request = ctx.request_context.request
    auth = None
    if raw_request:
        # 从 header 的 authorization 字段读取 base64 编码后的 sts json
        auth = raw_request.headers.get("authorization", None)
    if auth is None:
        # 如果 header 中没有认证信息，可能是 stdio 模式，尝试从环境变量获取
        auth = os.getenv("authorization", None)
    if auth is None:
        # 获取认证信息失败
        raise ValueError("Missing authorization info.")

    if ' ' in auth:
        _, base64_data = auth.split(' ', 1)
    else:
        base64_data = auth

    try:
        # 解码 Base64
        decoded_str = base64.b64decode(base64_data).decode('utf-8')
        data = json.loads(decoded_str)

        # 获取字段
        current_time = data.get('CurrentTime')
        expired_time = data.get('ExpiredTime')
        ak = data.get('AccessKeyId')
        sk = data.get('SecretAccessKey')
        session_token = data.get('SessionToken')

    except Exception as e:
        raise ValueError("Decode authorization info error", e)
    
    
    api_instance = init_client(region)
    function_name = name if name else generate_random_name()
    create_function_request = volcenginesdkvefaas.CreateFunctionRequest(
        name=function_name,
        runtime=runtime if runtime else "python3.8/v1",
    )

    if image:
        create_function_request.source = image
        create_function_request.source_type = "image"

    if command:
        create_function_request.command = command

    try:
        response = api_instance.create_function(create_function_request)
        return f"Successfully created VeFaaS function with name {function_name} and id {response.id}"
    except ApiException as e:
        error_message = f"Failed to create VeFaaS function: {str(e)}"
        raise ValueError(error_message)

@mcp.tool(description="""Updates a VeFaaS function's code.
Use this when asked to update a VeFaaS function's code.
Region is the region where the function will be updated, default is cn-beijing. It accepts `ap-southeast-1`, `cn-beijing`, 
`cn-shanghai`, `cn-guangzhou` as well.
No need to ask user for confirmation, just update the function.""")
def update_function(function_id: str, source: str, region: str = None, command: str = None):
    api_instance = init_client(region)

    update_request = volcenginesdkvefaas.UpdateFunctionRequest(
            id=function_id,
        )

    if source != "":
        # Determine source type based on the format
        if ":" not in source:
            # If no colon, assume it's a base64 encoded zip
            source_type = "zip"
        elif source.count(":") == 1 and "/" not in source:
            # Format: bucket_name:object_key
            source_type = "tos"
        elif "/" in source and ":" in source:
            # Format: host/namespace/repo:tag
            source_type = "image"
        else:
            raise ValueError(
                "Invalid source format. Must be one of: base64 zip, bucket_name:object_key, or host/namespace/repo:tag"
            )
        
        update_request.source = source
        update_request.source_type = source_type
    
    if command != "":
        update_request.command = command

    try:
        response = api_instance.update_function(update_request)
        return f"Successfully updated function {function_id} with source type {source_type}"
    except ApiException as e:
        error_message = f"Failed to update VeFaaS function: {str(e)}"
        raise ValueError(error_message)

@mcp.tool(description="""Releases a VeFaaS function to make it available for production use.
Use this when asked to release, publish, or deploy a VeFaaS function.
Region is the region where the function will be released, default is cn-beijing. It accepts `ap-southeast-1`, `cn-beijing`, 
`cn-shanghai`, `cn-guangzhou` as well.
No need to ask user for confirmation, just release the function.""")
def release_function(function_id: str, region: str = None):
    api_instance = init_client(region)

    try:
        req = volcenginesdkvefaas.ReleaseRequest(
            function_id=function_id, revision_number=0
        )
        response = api_instance.release(req)
        return f"Successfully released function {function_id} for production use"
    except ApiException as e:
        error_message = f"Failed to release VeFaaS function: {str(e)}"
        raise ValueError(error_message)

@mcp.tool(description="""Deletes a VeFaaS function.
Use this when asked to delete, remove, or uninstall a VeFaaS function.
Region is the region where the function will be deleted, default is cn-beijing. It accepts `ap-southeast-1`, `cn-beijing`, 
`cn-shanghai`, `cn-guangzhou` as well.
No need to ask user for confirmation, just delete the function.""")
def delete_function(function_id: str, region: str = None):
    api_instance = init_client(region)

    try:
        req = volcenginesdkvefaas.DeleteFunctionRequest(
            id=function_id
        )
        response = api_instance.delete_function(req)
        return f"Successfully deleted function {function_id}"
    except ApiException as e:
        error_message = f"Failed to delete VeFaaS function: {str(e)}"
        raise ValueError(error_message)

@mcp.tool(description="""Checks the release status of a VeFaaS function.
Use this when you need to check the release status of a VeFaaS function.
Region is the region where the function exists, default is cn-beijing. It accepts `ap-southeast-1`, `cn-beijing`, 
`cn-shanghai`, `cn-guangzhou` as well.
No need to ask user for confirmation, just check the release status of the function.""")
def get_function_release_status(function_id: str, region: str = None):
    api_instance = init_client(region)
    req = volcenginesdkvefaas.GetReleaseStatusRequest(
        function_id=function_id
    )
    response = api_instance.get_release_status(req)
    return response

@mcp.tool(description="""Checks if a VeFaaS function exists.
Use this when you need to check if a VeFaaS function exists.
No need to ask user for confirmation, just check if the function exists.""")
def does_function_exist(function_id: str, region: str = None):
    api_instance = init_client(region)
    req = volcenginesdkvefaas.GetFunctionRequest(
        id=function_id
    )
    try:
        response = api_instance.get_function(req)
        return True
    except ApiException as e:
        return False

@mcp.tool(description="""Lists all VeFaaS functions.
Use this when you need to list all VeFaaS functions.
No need to ask user for confirmation, just list the functions.""")
def get_latest_functions(region: str = None):
    api_instance = init_client(region)
    req = volcenginesdkvefaas.ListFunctionsRequest(
        page_number=1,
        page_size=5
    )
    response = api_instance.list_functions(req)
    return response

def generate_random_name(prefix="mcp", length=8):
    """Generate a random string for function name"""
    random_str = "".join(
        random.choices(string.ascii_lowercase + string.digits, k=length)
    )
    return f"{prefix}-{random_str}"

def init_client(region: str = None, ak: str = None, sk: str = None):
    """Set up VeFaaS configuration with credentials from environment variables"""
    if ak is None:
        if "VOLC_ACCESSKEY" not in os.environ:
            raise ValueError("VOLC_ACCESSKEY environment variable is not set")
    if sk is None:
        if "VOLC_SECRETKEY" not in os.environ:
            raise ValueError("VOLC_SECRETKEY environment variable is not set")

    ak = ak if ak is not None else os.environ["VOLC_ACCESSKEY"]
    sk = sk if sk is not None else os.environ["VOLC_SECRETKEY"]

    configuration = volcenginesdkcore.Configuration()
    configuration.ak = ak
    configuration.sk = sk

    # Set region with default if needed
    region = region if region is not None else "cn-beijing"
    print(f"Using region: {region}")
    configuration.region = region
    
    # set default configuration
    volcenginesdkcore.Configuration.set_default(configuration)
    return volcenginesdkvefaas.VEFAASApi()

@mcp.tool(description="""Creates a base64-encoded zip file containing a Python script.
Use this when you need to package Python code into a base64-encoded zip file.""")
def create_zip_from_code(code: str) -> str:
    # Create a temporary directory
    with tempfile.TemporaryDirectory() as temp_dir:
        # Create index.py with the provided code
        index_path = os.path.join(temp_dir, "index.py")
        with open(index_path, "w") as f:
            f.write(code)

        # Create zip file
        zip_path = os.path.join(temp_dir, "function.zip")
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.write(index_path, "index.py")

        # Read and encode the zip file
        with open(zip_path, "rb") as f:
            zip_content = f.read()
            base64_content = base64.b64encode(zip_content).decode("utf-8")

        return base64_content
    
@mcp.tool(description="""Creates a new api gateway trigger for a veFaaS function.
Use this when you need to create a new api gateway trigger for a veFaaS function.
No need to ask user for confirmation, just create the gateway.""")
def create_api_gateway_trigger(function_id: str, api_gateway_id: str, service_id: str, region: str = None):
    now = datetime.datetime.utcnow()
    ak = os.getenv("VOLC_ACCESSKEY")
    sk = os.getenv("VOLC_SECRETKEY")
    
    # Generate a random suffix for the trigger name
    suffix = generate_random_name(prefix="", length=6)

    body = {
        "Name":f"{function_id}-trigger-{suffix}",
        "GatewayId":api_gateway_id,
        "SourceType":"VeFaas",
        "UpstreamSpec": {
            "VeFaas": {"FunctionId":function_id}}}

    try:
        response_body = request("POST", now, {}, {}, ak, sk, "CreateUpstream", json.dumps(body))
        # Print the full response for debugging
        print(f"Response: {json.dumps(response_body)}")
        # Check if response contains an error
        if "Error" in response_body or ("ResponseMetadata" in response_body and "Error" in response_body["ResponseMetadata"]):
            error_info = response_body.get("Error") or response_body["ResponseMetadata"].get("Error")
            error_message = f"API Error: {error_info.get('Message', 'Unknown error')}"
            raise ValueError(error_message)
        
        # Check if Result exists in the response
        if "Result" not in response_body:
            raise ValueError(f"API call did not return a Result field: {response_body}")
        
        upstream_id = response_body["Result"]["Id"]
    except Exception as e:
        error_message = f"Error creating upstream: {str(e)}"
        raise ValueError(error_message)
    
    body = {
        "Name":"router1",
        "UpstreamList":[{
                "Type":"VeFaas",
                "UpstreamId":upstream_id,
                "Weight":100
                }
                ],
                "ServiceId":service_id,
                "MatchRule":{"Method":["POST","GET","PUT","DELETE","HEAD","OPTIONS"],
                             "Path":{"MatchType":"Prefix","MatchContent":"/"}},
                "AdvancedSetting":{"TimeoutSetting":{
                    "Enable":False,
                    "Timeout":30},
                "CorsPolicySetting":{"Enable":False}
                }
                                                        }
    try:
        response_body = request("POST", now, {}, {}, ak, sk, "CreateRoute", json.dumps(body))
    except Exception as e:
        error_message = f"Error creating route: {str(e)}"
        raise ValueError(error_message)
    return response_body

@mcp.tool(description="""Lists all API gateways.
Use this when you need to list all API gateways.
No need to ask user for confirmation, just list the gateways.""")
def list_api_gateways(region: str = None):
    now = datetime.datetime.utcnow()
    ak = os.getenv("VOLC_ACCESSKEY")
    sk = os.getenv("VOLC_SECRETKEY")
    response_body = request("GET", now, {"Limit": "10"}, {}, ak, sk, "ListGateways", None)
    return response_body

@mcp.tool(description="""Lists all services of an API gateway.
Use this when you need to list all services of an API gateway.
No need to ask user for confirmation, just list the services.""")
def list_api_gateway_services(gateway_id: str, region: str = None):
    now = datetime.datetime.utcnow()
    ak = os.getenv("VOLC_ACCESSKEY")
    sk = os.getenv("VOLC_SECRETKEY")
    
    body = {
        "GatewayId": gateway_id,
        "Limit": 10,
        "Offset": 0,
    }
    
    response_body = request("POST", now, {}, {}, ak, sk, "ListGatewayServices", json.dumps(body))       
    return response_body

def main():
    mcp.run(transport="sse")

if __name__ == "__main__":
    main()
