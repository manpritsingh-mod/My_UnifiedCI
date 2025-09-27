Complete Jenkins MCP Setup Guide - From Scratch
What is MCP and Why Use It?
MCP (Model Context Protocol) is like a translator between AI assistants (like GitHub Copilot, ChatGPT, Claude) and Jenkins. Instead of manually clicking through Jenkins, you can ask AI questions like:
    • "Why did my build fail?"
    • "Start a new build for my project"
    • "Show me the test results"
And the AI will understand and interact with Jenkins for you!
Prerequisites (What You Need First)
Step 1: Make Sure You Have These Ready
    1. Jenkins Server running (version 2.401 or newer)
    2. Admin access to Jenkins
    3. Basic terminal/command line knowledge
    4. Internet connection for downloading plugins
Step 2: Check Your Jenkins Version
bash
# Go to Jenkins → Manage Jenkins → System Information
# Look for "Jenkins version" - should be 2.401+
Method 1: Installing the Official Jenkins MCP Plugin (Recommended)
Step 3: Install the MCP Server Plugin
Option A: Through Jenkins Web Interface (Easy Way)
    1. Open your Jenkins in browser (e.g., http://localhost:8080)
    2. Click "Manage Jenkins"
    3. Click "Plugins"
    4. Click "Available plugins" tab
    5. Search for "MCP Server"
    6. Check the box next to "MCP Server Plugin"
    7. Click "Install"
    8. Wait for installation to complete
    9. Restart Jenkins when prompted
Option B: Through Command Line
bash
# Download the plugin file
wget https://updates.jenkins.io/latest/mcp-server.hpi
# Copy to Jenkins plugins directory
sudo cp mcp-server.hpi /var/lib/jenkins/plugins/
# Restart Jenkins
sudo systemctl restart jenkins
Step 4: Configure the MCP Plugin
    1. Go to Jenkins → Manage Jenkins → System
    2. Scroll down to find "MCP Server Configuration" section
    3. Enable "Enable MCP Server" checkbox
    4. Set Port: 8081 (or any available port)
    5. Set Host: localhost (or your server IP)
    6. Click "Save"
Step 5: Generate Jenkins API Token
    1. Click your username in top-right corner
    2. Click "Security"
    3. Under "API Token" section, click "Add new Token"
    4. Give it a name: MCP-Access
    5. Click "Generate"
    6. Copy and save the token - you'll need this later!
Example token: 11a8b4c5d6e7f8g9h0i1j2k3l4m5n6o7p8q9r0s1
Method 2: External MCP Server (Alternative Approach)
Step 6: Install External MCP Server
Option A: Using uvx (Recommended)
bash
# Install uvx if you don't have it
pip install uvx
# Install Jenkins MCP server
uvx jenkins-mcp
Option B: Using npm
bash
# Install Node.js first if you don't have it
npm install -g jenkins-mcp
Option C: From GitHub
bash
# Clone the repository
git clone https://github.com/lanbaoshen/mcp-jenkins.git
cd mcp-jenkins
# Install dependencies
pip install -r requirements.txt
# Run the server
python -m mcp_jenkins
Step 7: Configure External MCP Server
Create a configuration file mcp-jenkins-config.json:
json
{
  "jenkins_url": "http://localhost:8080",
  "jenkins_username": "your-username",
  "jenkins_token": "your-api-token-from-step-5",
  "port": 9887,
  "read_only": false
}
Run the server:
bash
# Stdio Mode (most compatible)
uvx mcp-jenkins --jenkins-url http://localhost:8080 --jenkins-username myuser --jenkins-password mytoken123 --read-only
# SSE Mode (Server-Sent Events)
uvx mcp-jenkins --jenkins-url http://localhost:8080 --jenkins-username myuser --jenkins-password mytoken123 --transport sse --port 9887
Step 8: Connect AI Assistants
For GitHub Copilot (VS Code)
    1. Open VS Code
    2. Install GitHub Copilot extension
    3. Open settings (Ctrl+,)
    4. Search for "MCP"
    5. Add MCP server configuration:
json
{
  "copilot.mcp.servers": {
    "jenkins": {
      "url": "http://localhost:8081",
      "auth": {
        "type": "basic",
        "username": "your-jenkins-username",
        "token": "your-api-token"
      }
    }
  }
}
For Claude Desktop
Create/edit ~/.config/claude/mcp_servers.json:
json
{
  "mcpServers": {
    "jenkins-mcp": {
      "command": "uvx",
      "args": ["jenkins-mcp"],
      "env": {
        "JENKINS_URL": "http://localhost:8080",
        "JENKINS_USERNAME": "your-username",
        "JENKINS_TOKEN": "your-api-token"
      }
    }
  }
}
For ChatGPT/OpenAI
Use OpenAI's MCP client:
bash
npm install @openai/mcp-client
# Create connection script
node connect-jenkins.js
Step 9: Test Your Setup
Test 1: Basic Connection
bash
# Test if MCP server is running
curl http://localhost:8081/health
# Expected response: {"status": "ok"}
Test 2: Jenkins API Access
bash
# Test Jenkins API with your token
curl -u "username:your-api-token" http://localhost:8080/api/json
# Should return Jenkins information in JSON format
Test 3: Ask AI Assistant
Try these example prompts:
    • "Show me my Jenkins jobs"
    • "What's the status of my last build?"
    • "Why did build #123 fail?"
    • "Trigger a new build for project-name"
Step 10: Common Examples and Use Cases
Example 1: Check Build Status
You ask: "What's the status of my latest build?"
AI does:
    1. Connects to Jenkins via MCP
    2. Fetches latest build information
    3. Returns: "Your latest build (#45) for 'my-app' completed successfully 10 minutes ago"
Example 2: Debug Failed Build
You ask: "My build failed, what went wrong?"
AI does:
    1. Identifies the failed build
    2. Reads console logs
    3. Analyzes error messages
    4. Returns: "Build failed due to missing dependency 'axios'. Run 'npm install axios' in your project."
Example 3: Start New Build
You ask: "Start a build for my backend service"
AI does:
    1. Finds the correct job
    2. Triggers the build
    3. Returns: "Started build #46 for 'backend-service'. Build is currently running."
Troubleshooting Common Issues
Issue 1: "Connection Refused"
Problem: AI can't connect to Jenkins Solution:
bash
# Check if Jenkins is running
sudo systemctl status jenkins
# Check if MCP server is running
ps aux | grep mcp
# Verify port is open
netstat -tulpn | grep :8081
Issue 2: "Authentication Failed"
Problem: API token doesn't work Solution:
    1. Regenerate API token in Jenkins
    2. Update your MCP configuration
    3. Restart MCP server
Issue 3: "Plugin Not Found"
Problem: MCP plugin not installed properly Solution:
bash
# Check if plugin is installed
ls /var/lib/jenkins/plugins/ | grep mcp
# Restart Jenkins
sudo systemctl restart jenkins
Security Best Practices
Step 11: Secure Your Setup
    1. Use Read-Only Mode for Testing
bash
uvx mcp-jenkins --jenkins-url http://localhost:8080 --jenkins-username myuser --jenkins-password mytoken --read-only
    2. Limit API Token Permissions
    • Create a dedicated Jenkins user for MCP
    • Give minimal required permissions
    • Regularly rotate API tokens
    3. Use HTTPS
json
{
  "jenkins_url": "https://your-jenkins.com",
  "ssl_verify": true
}
    4. Network Security
bash
# Only allow local connections
--host 127.0.0.1
# Use non-standard port
--port 9999
Advanced Configuration
Step 12: Custom AI Prompts
Create custom prompt templates for common tasks:
Build Status Template:
Current build status for {{job_name}}:
- Build Number: {{build_number}}
- Status: {{status}}
- Duration: {{duration}}
- Last Success: {{last_success}}
Error Analysis Template:
Build failure analysis for {{job_name}} #{{build_number}}:
- Error Type: {{error_type}}
- Failed Stage: {{failed_stage}}
- Suggestion: {{suggestion}}
- Quick Fix: {{quick_fix}}
What You Can Do Now
After setup, you can use natural language to:
    1. Monitor Builds 
        ○ "Show all running builds"
        ○ "Which builds failed today?"
        ○ "What's the build queue status?"
    2. Debug Issues 
        ○ "Analyze the last failed build"
        ○ "What tests are failing?"
        ○ "Show me error logs for build #50"
    3. Manage Jobs 
        ○ "Trigger deployment to staging"
        ○ "Stop the running build"
        ○ "Schedule a build for tonight"
    4. Get Insights 
        ○ "Show build trends this week"
        ○ "Which job takes longest to build?"
        ○ "Compare success rates between branches"
Next Steps
    1. Set up more AI assistants (Claude, ChatGPT, etc.)
    2. Create custom workflows for your team
    3. Add more Jenkins plugins for extended functionality
    4. Set up notifications for build events
    5. Create dashboards showing AI interactions
Support and Resources
    • Jenkins MCP Plugin: https://plugins.jenkins.io/mcp-server/
    • MCP Specification: https://modelcontextprotocol.io/
    • GitHub Repository: https://github.com/jenkinsci/mcp-server-plugin
    • Community Forum: https://community.jenkins.io/

Congratulations! 🎉 You now have Jenkins connected to AI assistants through MCP. You can ask questions in natural language instead of clicking through the Jenkins interface!
